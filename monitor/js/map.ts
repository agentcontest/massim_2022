import { h, VNode } from 'snabbdom';

import { Pos, Entity, Block, StaticWorld, DynamicWorld, Positionable } from './interfaces';
import { Ctrl } from './ctrl';
import { compareEntity, samePos, taxicab } from './util';
import * as styles from './styles';

interface Transform {
  x: number;
  y: number;
  scale: number;
}

interface Dragging {
  first: [number, number];
  latest: [number, number];
  started: boolean;
}

interface Zoom {
  center: [number, number];
  distance: number;
}

interface Zooming {
  initialTransform: Transform;
  zoom: Zoom;
}

export interface MapViewModel {
  dragging?: Dragging;
  zooming?: Zooming;
  transform: Transform;
  selected?: number; // agent.id
}

export interface MapViewOpts {
  size?: number;
  viewOnly?: boolean;
}

export const minScale = 10;
export const maxScale = 100;

export class MapCtrl {
  readonly vm: MapViewModel;

  constructor(readonly root: Ctrl) {
    this.vm = {
      transform: {
        x: 0,
        y: 0,
        scale: 20,
      },
    };
  }

  selectedEntity(): Entity | undefined {
    if (!this.root.vm.dynamic) return;
    return this.root.vm.dynamic.entities.find(a => a.id === this.vm.selected);
  }

  nextSelection(pos?: Pos): Entity | undefined {
    if (!pos || !this.root.vm.dynamic) return;
    const selected = this.selectedEntity();

    const agents = this.root.vm.dynamic.entities.filter(a => samePos(a.pos, pos));
    agents.reverse(); // opposite of rendering order
    return agents.find(a => !selected || compareEntity(selected, a));
  }

  select(pos?: Pos) {
    this.vm.selected = this.nextSelection(pos)?.id;
    this.root.redraw();
  }

  invPos(pos: [number, number], bounds: DOMRect): Pos | undefined {
    // relative to bounds
    const x = pos[0] - bounds.x;
    const y = pos[1] - bounds.y;
    if (x < 0 || x > bounds.width || y < 0 || y > bounds.height) return;

    // relative to transform
    const p: Pos = [
      Math.floor((x - this.vm.transform.x) / this.vm.transform.scale),
      Math.floor((y - this.vm.transform.y) / this.vm.transform.scale),
    ];

    // relative to grid
    if (this.root.vm.static) {
      return [mod(p[0], this.root.vm.static.grid.width), mod(p[1], this.root.vm.static.grid.height)];
    } else return p;
  }

  zoom(center: [number, number], factor: number): void {
    if (this.vm.transform.scale * factor < minScale) factor = minScale / this.vm.transform.scale;
    if (this.vm.transform.scale * factor > maxScale) factor = maxScale / this.vm.transform.scale;
    this.vm.transform = {
      x: center[0] + (this.vm.transform.x - center[0]) * factor,
      y: center[1] + (this.vm.transform.y - center[1]) * factor,
      scale: this.vm.transform.scale * factor,
    };
  }
}

export function mapView(ctrl: MapCtrl, opts?: MapViewOpts): VNode {
  return h('canvas', {
    attrs: opts?.size
      ? {
          width: opts.size,
          height: opts.size,
        }
      : undefined,
    hook: {
      insert(vnode) {
        const elm = vnode.elm as HTMLCanvasElement;

        if (opts?.size) render(elm, ctrl, opts);
        else
          new (window as any).ResizeObserver((entries: any) => {
            for (const entry of entries) {
              elm.width = entry.contentRect.width;
              elm.height = entry.contentRect.height;
              requestAnimationFrame(() => render(elm, ctrl, opts));
            }
          }).observe(elm);

        const mouseup = (ev: Event) => {
          if (ctrl.vm.dragging || ctrl.vm.zooming) ev.preventDefault();
          if (ctrl.vm.dragging && !ctrl.vm.dragging.started) {
            const pos = eventPosition(ev) || ctrl.vm.dragging.first;
            ctrl.select(ctrl.invPos(pos, elm.getBoundingClientRect()));
          }
          ctrl.vm.dragging = undefined;
          ctrl.vm.zooming = undefined;
        };

        const mousemove = (ev: Partial<MouseEvent & TouchEvent> & Event) => {
          const zoom = eventZoom(ev);
          if (ctrl.vm.zooming && zoom) {
            ctrl.vm.transform = { ...ctrl.vm.zooming.initialTransform };
            ctrl.zoom(
              [
                (ctrl.vm.zooming.zoom.center[0] + zoom.center[0]) / 2,
                (ctrl.vm.zooming.zoom.center[1] + zoom.center[1]) / 2,
              ],
              zoom.distance / ctrl.vm.zooming.zoom.distance
            );
            ev.preventDefault();
            return;
          }

          const pos = eventPosition(ev);
          if (pos) {
            const inv = ctrl.invPos(pos, elm.getBoundingClientRect());
            if (inv) ctrl.root.setHover(inv);
          }

          if (ctrl.vm.dragging && pos) {
            if (ctrl.vm.dragging.started || distanceSq(ctrl.vm.dragging.first, pos) > 20 * 20) {
              ctrl.vm.dragging.started = true;
              ctrl.vm.transform.x += pos[0] - ctrl.vm.dragging.latest[0];
              ctrl.vm.transform.y += pos[1] - ctrl.vm.dragging.latest[1];
              ctrl.vm.dragging.latest = pos;
            }
            ev.preventDefault();
          }
        };

        const mousedown = (ev: Partial<MouseEvent & TouchEvent> & Event) => {
          if (ev.button !== undefined && ev.button !== 0) return; // only left click
          const pos = eventPosition(ev);
          const zoom = eventZoom(ev);
          if (zoom) {
            ctrl.vm.zooming = {
              initialTransform: { ...ctrl.vm.transform },
              zoom,
            };
          } else if (pos) {
            ctrl.vm.dragging = {
              first: pos,
              latest: pos,
              started: false,
            };
          }
          if (zoom || pos) {
            ev.preventDefault();
            requestAnimationFrame(() => render(ev.target as HTMLCanvasElement, ctrl, opts, true));
          }
        };

        const wheel = (ev: WheelEvent) => {
          ev.preventDefault();
          ctrl.zoom([ev.offsetX, ev.offsetY], Math.pow(3 / 2, -ev.deltaY / (ev.deltaMode ? 6.25 : 100)));
          requestAnimationFrame(() => render(ev.target as HTMLCanvasElement, ctrl, opts));
        };

        (elm as any).massim = {
          unbinds: opts?.viewOnly
            ? [unbindable(document, 'mousemove', mousemove, { passive: false })]
            : [
                unbindable(elm, 'mousedown', mousedown, { passive: false }),
                unbindable(elm, 'touchstart', mousedown, { passive: false }),
                unbindable(elm, 'wheel', wheel, { passive: false }),
                unbindable(document, 'mouseup', mouseup),
                unbindable(document, 'touchend', mouseup),
                unbindable(document, 'mousemove', mousemove, { passive: false }),
                unbindable(document, 'touchmove', mousemove, { passive: false }),
              ],
        };
      },
      update(_, vnode) {
        render(vnode.elm as HTMLCanvasElement, ctrl, opts);
      },
      destroy(vnode) {
        const unbinds = (vnode.elm as any).massim?.unbinds;
        if (unbinds) for (const unbind of unbinds) unbind();
      },
    },
  });
}

function unbindable(el: EventTarget, eventName: string, callback: EventListener, options?: AddEventListenerOptions) {
  el.addEventListener(eventName, callback, options);
  return () => el.removeEventListener(eventName, callback, options);
}

function eventZoom(e: Partial<TouchEvent>): Zoom | undefined {
  if (e.targetTouches?.length !== 2) return;
  return {
    center: [
      (e.targetTouches[0].clientX + e.targetTouches[1].clientX) / 2,
      (e.targetTouches[0].clientY + e.targetTouches[1].clientY) / 2,
    ],
    distance: Math.max(
      20,
      Math.hypot(
        e.targetTouches[0].clientX - e.targetTouches[1].clientX,
        e.targetTouches[0].clientY - e.targetTouches[1].clientY
      )
    ),
  };
}

function eventPosition(e: Partial<MouseEvent & TouchEvent>): [number, number] | undefined {
  if (e.clientX || e.clientX === 0) return [e.clientX, e.clientY!];
  if (e.targetTouches?.[0]) return [e.targetTouches[0].clientX, e.targetTouches[0].clientY];
  return;
}

function distanceSq(a: [number, number], b: [number, number]): number {
  const dx = a[0] - b[0];
  const dy = a[1] - b[1];
  return dx * dx + dy * dy;
}

function mod(a: number, b: number): number {
  return ((a % b) + b) % b;
}

function render(canvas: HTMLCanvasElement, ctrl: MapCtrl, opts: MapViewOpts | undefined, raf = false) {
  const vm = ctrl.vm;
  const width = canvas.width,
    height = canvas.height;

  const ctx = canvas.getContext('2d')!;
  ctx.save();

  // font
  ctx.textAlign = 'center';
  ctx.font = '0.4px Helvetica';

  // fill background
  ctx.fillStyle = '#eee';
  ctx.fillRect(0, 0, width, height);

  // draw grid
  const transform = ctrl.vm.transform;
  const selectedEntity = ctrl.selectedEntity();
  if (opts?.viewOnly && selectedEntity) {
    // auto center to selection
    transform.scale = Math.min(canvas.width, canvas.height) / (selectedEntity.vision * 2 + 3);
    transform.x = canvas.width / 2 - (selectedEntity.pos[0] + 0.5) * transform.scale;
    transform.y = canvas.height / 2 - (selectedEntity.pos[1] + 0.5) * transform.scale;
  }
  ctx.translate(transform.x, transform.y);
  ctx.scale(transform.scale, transform.scale);

  const ymin = Math.floor(-transform.y / transform.scale);
  const xmin = Math.floor(-transform.x / transform.scale);
  const ymax = ymin + Math.ceil(canvas.height / transform.scale);
  const xmax = xmin + Math.ceil(canvas.width / transform.scale);

  ctx.fillStyle = '#ddd';
  for (let y = ymin; y <= ymax; y++) {
    for (let x = xmin + ((((xmin + y) % 2) + 2) % 2); x <= xmax; x += 2) {
      ctx.fillRect(x, y, 1, 1);
    }
  }

  if (ctrl.root.vm.static && ctrl.root.vm.dynamic) {
    const grid = ctrl.root.vm.static.grid,
      maxEnergy = ctrl.root.vm.static.maxEnergy;

    const criminals = new Set(ctrl.root.vm.dynamic.violations.map(v => v.who));

    for (let dy = Math.floor(ymin / grid.height) * grid.height; dy <= ymax + grid.height; dy += grid.height) {
      for (let dx = Math.floor(xmin / grid.width) * grid.width; dx <= xmax + grid.width; dx += grid.width) {
        // goal zones
        ctx.fillStyle = styles.goalZone;
        for (const zone of ctrl.root.vm.dynamic.goalZones) {
          selectArea(ctx, dx + zone.pos[0], dy + zone.pos[1], zone.r);
          ctx.fill();
        }

        // role zones
        ctx.fillStyle = styles.roleZone;
        for (const zone of ctrl.root.vm.dynamic.roleZones) {
          selectArea(ctx, dx + zone.pos[0], dy + zone.pos[1], zone.r);
          ctx.fill();
        }

        // obstacles
        ctx.fillStyle = styles.obstacle;
        for (const obstacle of ctrl.root.vm.dynamic.obstacles) {
          if (visible(xmin, xmax, ymin, ymax, obstacle.pos, dx, dy)) {
            ctx.fillRect(dx + obstacle.pos[0] - 0.04, dy + obstacle.pos[1] - 0.04, 1.08, 1.08);
          }
        }

        // draw axis
        ctx.globalCompositeOperation = 'difference';
        ctx.strokeStyle = 'white';
        ctx.lineWidth = 0.3;
        ctx.beginPath();
        ctx.moveTo(dx - 1.5, dy);
        ctx.lineTo(dx + 1.5, dy);
        ctx.moveTo(dx, dy - 1.5);
        ctx.lineTo(dx, dy + 1.5);
        ctx.stroke();
        ctx.globalCompositeOperation = 'source-over';

        // dispensers
        for (const dispenser of ctrl.root.vm.dynamic.dispensers) {
          if (visible(xmin, xmax, ymin, ymax, dispenser.pos, dx, dy)) {
            ctx.lineWidth = 2 * 0.025;
            const color = styles.blocks[ctrl.root.vm.static.blockTypes.indexOf(dispenser.type) % styles.blocks.length];
            const r1 = rect(1, dx + dispenser.pos[0], dy + dispenser.pos[1], 0.025);
            drawBlock(ctx, r1, color, 'white', 'black');
            const r2 = rect(1, dx + dispenser.pos[0], dy + dispenser.pos[1], 5 * 0.025);
            drawBlock(ctx, r2, color, 'white', 'black');
            const r3 = rect(1, dx + dispenser.pos[0], dy + dispenser.pos[1], 9 * 0.025);
            drawBlock(ctx, r3, color, 'white', 'black');
            ctx.fillStyle = 'white';
            ctx.fillText(
              dispenser.type,
              dx + dispenser.pos[0] + 0.5,
              dy + dispenser.pos[1] + 0.5 + helveticaBaseline(0.4)
            );
          }
        }

        // blocks
        drawBlocks(
          ctx,
          dx,
          dy,
          ctrl.root.vm.static,
          ctrl.root.vm.dynamic.blocks.filter(b => visible(xmin, xmax, ymin, ymax, b.pos, dx, dy))
        );

        // agents
        for (const agent of ctrl.root.vm.dynamic.entities) {
          if (visible(xmin, xmax, ymin, ymax, agent.pos, dx, dy)) {
            const teamIndex = ctrl.root.vm.teamNames.indexOf(agent.team);
            drawEntity(ctx, dx, dy, agent, teamIndex);

            if (
              criminals.has(agent.name) &&
              agent.id != ctrl.vm.selected &&
              (!ctrl.root.vm.hover || !samePos(agent.pos, ctrl.root.vm.hover))
            ) {
              drawEnergyBar(ctx, dx, dy, agent.pos, 0.9, agent.energy / maxEnergy);
            }
          }

          // agent action
          if (agent.action == 'clear' && agent.actionResult.indexOf('failed_') != 0) {
            const x = dx + agent.pos[0] + parseInt(agent.actionParams[0], 10);
            const y = dy + agent.pos[1] + parseInt(agent.actionParams[1], 10);
            ctx.lineWidth = 0.05;
            ctx.strokeStyle = 'red';
            selectArea(ctx, x, y, 1);
            ctx.stroke();
          }
        }

        // attachables of selected agent
        if (selectedEntity?.attached) {
          ctx.fillStyle = styles.hover;
          for (const attached of selectedEntity.attached) {
            if (!samePos(attached, selectedEntity.pos)) {
              ctx.fillRect(dx + attached[0], dy + attached[1], 1, 1);
            }
          }
        }

        // clear events
        for (const clear of ctrl.root.vm.dynamic.clear) {
          ctx.lineWidth = 0.1;
          ctx.strokeStyle = 'red';
          selectArea(ctx, dx + clear.pos[0], dy + clear.pos[1], clear.r);
          ctx.stroke();
        }

        // hover
        if (ctrl.root.vm.hover) {
          drawHover(
            ctx,
            ctrl,
            ctrl.root.vm.static,
            ctrl.root.vm.dynamic,
            ctrl.root.vm.teamNames,
            dx,
            dy,
            ctrl.root.vm.hover
          );
        }
      }
    }

    // fog of war
    for (let dy = Math.floor(ymin / grid.height) * grid.height; dy <= ymax + grid.height; dy += grid.height) {
      for (let dx = Math.floor(xmin / grid.width) * grid.width; dx <= xmax + grid.width; dx += grid.width) {
        for (const agent of ctrl.root.vm.dynamic.entities) {
          if (agent.id === ctrl.vm.selected) {
            drawFogOfWar(ctx, ctrl.root.vm.static, dx, dy, agent);
            drawEnergyBar(ctx, dx, dy, agent.pos, 2, agent.energy / ctrl.root.vm.static.maxEnergy);
          }
        }
      }
    }
  }

  ctx.restore();

  if (raf && (vm.dragging || vm.zooming)) {
    requestAnimationFrame(() => render(canvas, ctrl, opts, true));
  }
}

function visible(xmin: number, xmax: number, ymin: number, ymax: number, pos: Pos, dx: number, dy: number): boolean {
  return xmin <= pos[0] + dx && pos[0] + dx <= xmax && ymin <= pos[1] + dy && pos[1] + dy <= ymax;
}

function drawFogOfWar(ctx: CanvasRenderingContext2D, st: StaticWorld, dx: number, dy: number, agent: Entity) {
  ctx.fillStyle = 'rgba(0, 0, 0, 0.3)';
  const top = dy - st.grid.height + agent.pos[1] + agent.vision + 1;
  ctx.fillRect(dx, top, st.grid.width, st.grid.height - 2 * agent.vision - 1); // above
  ctx.fillRect(
    dx - st.grid.width + agent.pos[0] + agent.vision + 1,
    dy + agent.pos[1] - agent.vision,
    st.grid.width - 2 * agent.vision - 1,
    2 * agent.vision + 1
  );
  for (let x = -agent.vision; x <= agent.vision; x++) {
    for (let y = -agent.vision; y <= agent.vision; y++) {
      if (Math.abs(x) + Math.abs(y) > agent.vision) {
        ctx.fillRect(dx + agent.pos[0] + x, dy + agent.pos[1] + y, 1, 1);
      }
    }
  }
}

function drawHover(
  ctx: CanvasRenderingContext2D,
  ctrl: MapCtrl,
  st: StaticWorld,
  world: DynamicWorld,
  teamNames: string[],
  dx: number,
  dy: number,
  hover: Pos
) {
  if (hover[0] < 0 || hover[0] >= st.grid.width || hover[1] < 0 || hover[1] >= st.grid.height) return;
  ctx.beginPath();
  ctx.fillStyle = styles.hover;
  ctx.fillRect(dx + hover[0], dy + hover[1], 1, 1);

  for (const attachable of (world.entities as Array<Entity | Block>).concat(world.blocks)) {
    if (samePos(attachable.pos, hover) && attachable.attached) {
      for (const pos of attachable.attached) {
        ctx.fillRect(dx + pos[0], dy + pos[1], 1, 1);
      }
    }
  }

  const nextSelection = ctrl.nextSelection(hover);

  ctx.lineWidth = 0.1;
  for (const agent of world.entities) {
    if (taxicab(agent.pos, hover) <= agent.vision) {
      ctx.strokeStyle = styles.team(teamNames.indexOf(agent.team)).background;
      selectArea(ctx, dx + agent.pos[0], dy + agent.pos[1], 5);
      ctx.stroke();

      if (nextSelection && agent.id == nextSelection.id) {
        drawEnergyBar(ctx, dx, dy, agent.pos, 1.2, agent.energy / st.maxEnergy);
      }
    }
  }
}

interface Rect {
  x1: number;
  y1: number;
  x2: number;
  y2: number;
  width: number;
  height: number;
}

function rect(blockSize: number, x: number, y: number, margin: number): Rect {
  return {
    x1: x * blockSize + margin,
    y1: y * blockSize + margin,
    x2: x * blockSize + blockSize - margin,
    y2: y * blockSize + blockSize - margin,
    width: blockSize - 2 * margin,
    height: blockSize - 2 * margin,
  };
}

function drawEnergyBar(ctx: CanvasRenderingContext2D, dx: number, dy: number, pos: Pos, size: number, ratio: number) {
  ctx.fillStyle = 'red';
  ctx.fillRect(dx + pos[0] + 0.5 - size / 2, dy + pos[1] - size * 0.2, size, size * 0.1);
  ctx.fillStyle = 'green';
  ctx.fillRect(dx + pos[0] + 0.5 - size / 2, dy + pos[1] - size * 0.2, size * ratio, size * 0.1);
}

interface DrawEntity extends Positionable {
  name?: string;
}

export function drawEntity(
  ctx: CanvasRenderingContext2D,
  dx: number,
  dy: number,
  agent: DrawEntity,
  teamIndex: number
) {
  ctx.lineWidth = 0.125;
  ctx.strokeStyle = 'black';

  ctx.beginPath();
  ctx.moveTo(dx + agent.pos[0] + 0.5, dy + agent.pos[1]);
  ctx.lineTo(dx + agent.pos[0] + 0.5, dy + agent.pos[1] + 1);
  ctx.stroke();

  ctx.beginPath();
  ctx.moveTo(dx + agent.pos[0], dy + agent.pos[1] + 0.5);
  ctx.lineTo(dx + agent.pos[0] + 1, dy + agent.pos[1] + 0.5);
  ctx.stroke();

  const style = styles.team(teamIndex);
  if (teamIndex % 2 === 0) {
    ctx.lineWidth = 0.05;
    const margin = (1 - 15 / 16 / Math.sqrt(2)) / 2;
    const r = rect(1, dx + agent.pos[0], dy + agent.pos[1], margin);
    drawBlock(ctx, r, style.background, 'white', 'black');
  } else {
    ctx.lineWidth = 0.04;
    const r = rect(1, dx + agent.pos[0], dy + agent.pos[1], 0.0625);
    drawRotatedBlock(ctx, r, style.background, 'white', 'black');
  }

  if (agent.name) {
    ctx.fillStyle = style.color;
    ctx.fillText(
      shortEntityName(agent.name),
      dx + agent.pos[0] + 0.5,
      dy + agent.pos[1] + 0.5 + helveticaBaseline(0.4)
    );
  }
}

export function drawBlocks(ctx: CanvasRenderingContext2D, dx: number, dy: number, st: StaticWorld, blocks: Block[]) {
  for (const block of blocks) {
    ctx.lineWidth = 0.05;
    const r = rect(1, dx + block.pos[0], dy + block.pos[1], 0.025);
    drawBlock(ctx, r, styles.blocks[st.blockTypes.indexOf(block.type) % styles.blocks.length], 'white', 'black');

    ctx.textAlign = 'center';
    ctx.fillStyle = 'white';
    ctx.font = '0.5px Helvetica';
    ctx.fillText(block.type, dx + block.pos[0] + 0.5, dy + block.pos[1] + 0.5 + helveticaBaseline(0.5));
  }
}

function drawBlock(ctx: CanvasRenderingContext2D, r: Rect, color: string, light: string, dark: string) {
  ctx.fillStyle = color;
  ctx.fillRect(r.x1, r.y1, r.width, r.height);

  ctx.beginPath();
  ctx.moveTo(r.x1, r.y2);
  ctx.lineTo(r.x1, r.y1);
  ctx.lineTo(r.x2, r.y1);
  ctx.strokeStyle = light;
  ctx.stroke();

  ctx.beginPath();
  ctx.moveTo(r.x2, r.y1);
  ctx.lineTo(r.x2, r.y2);
  ctx.lineTo(r.x1, r.y2);
  ctx.strokeStyle = dark;
  ctx.stroke();
}

function selectArea(ctx: CanvasRenderingContext2D, x: number, y: number, radius: number) {
  ctx.beginPath();
  ctx.moveTo(x - radius, y + 0.5);
  ctx.lineTo(x + 0.5, y - radius);
  ctx.lineTo(x + 1 + radius, y + 0.5);
  ctx.lineTo(x + 0.5, y + radius + 1);
  ctx.lineTo(x - radius, y + 0.5);
  ctx.closePath();
}

function drawRotatedBlock(ctx: CanvasRenderingContext2D, r: Rect, color: string, light: string, dark: string) {
  ctx.beginPath();
  ctx.fillStyle = color;
  ctx.moveTo(r.x1, (r.y1 + r.y2) / 2);
  ctx.lineTo((r.x1 + r.x2) / 2, r.y1);
  ctx.lineTo(r.x2, (r.y1 + r.y2) / 2);
  ctx.lineTo((r.x1 + r.x2) / 2, r.y2);
  ctx.closePath();
  ctx.fill();

  ctx.beginPath();
  ctx.moveTo(r.x1, (r.y1 + r.y2) / 2);
  ctx.lineTo((r.x1 + r.x2) / 2, r.y1);
  ctx.lineTo(r.x2, (r.y1 + r.y2) / 2);
  ctx.strokeStyle = light;
  ctx.stroke();

  ctx.beginPath();
  ctx.moveTo(r.x2, (r.y1 + r.y2) / 2);
  ctx.lineTo((r.x1 + r.x2) / 2, r.y2);
  ctx.lineTo(r.x1, (r.y1 + r.y2) / 2);
  ctx.strokeStyle = dark;
  ctx.stroke();
}

function shortEntityName(name: string): string {
  if (name.startsWith('agent')) name = name.slice('agent'.length);
  const match = name.match(/^-?[A-Za-z][A-Za-z-_]*([0-9]+)$/);
  return match ? match[1] : name;
}

function helveticaBaseline(size: number) {
  return size * 0.33;
}
