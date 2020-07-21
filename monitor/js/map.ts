import { h } from 'snabbdom';
import { VNode } from 'snabbdom/vnode';

import { Ctrl } from './ctrl';

export interface MapTransform {
  x: number;
  y: number;
  scale: number;
}

export interface MapViewModel {
  mousedown?: [number, number];
  mousemove?: [number, number];

  transform: MapTransform;
}

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

  transform() {
    if (this.vm.mousedown && this.vm.mousemove) {
      return {
        x: this.vm.transform.x + this.vm.mousemove[0] - this.vm.mousedown[0],
        y: this.vm.transform.y + this.vm.mousemove[1] - this.vm.mousedown[1],
        scale: this.vm.transform.scale,
      }
    } else return this.vm.transform;
  }
}

export function mapView(ctrl: MapCtrl): VNode {
  return h('canvas', {
    hook: {
      insert(vnode) {
        const elm = vnode.elm as HTMLCanvasElement;

        new (window as any)['ResizeObserver']((entries: any) => {
          for (const entry of entries) {
            elm.width = entry.contentRect.width;
            elm.height = entry.contentRect.height;
            requestAnimationFrame(() => render(elm, ctrl));
          }
        }).observe(elm);

        if (!vnode.data) vnode.data = {};

        vnode.data.massim = {
          mouseup(ev: MouseEvent) {
            if (ctrl.vm.mousedown && ctrl.vm.mousemove) {
              ctrl.vm.transform.x += ctrl.vm.mousemove[0] - ctrl.vm.mousedown[0];
              ctrl.vm.transform.y += ctrl.vm.mousemove[1] - ctrl.vm.mousedown[1];
            }
            ctrl.vm.mousedown = ctrl.vm.mousemove = undefined;
          },
          mousemove(ev: MouseEvent) {
            if (ctrl.vm.mousedown) {
              ctrl.vm.mousemove = [ev.clientX, ev.clientY];
            }
          }
        };
        document.addEventListener('mouseup', vnode.data.massim.mouseup);
        document.addEventListener('mousemove', vnode.data.massim.mousemove);
      },
      update(_, vnode) {
        render(vnode.elm as HTMLCanvasElement, ctrl);
      },
      destroy(vnode) {
        if (vnode.data) {
          document.removeEventListener('mouseup', vnode.data.massim.mouseup);
          document.removeEventListener('mousemove', vnode.data.massim.mousemove);
        }
      },
    },
    on: {
      mousedown(ev) {
        ev.preventDefault();
        ctrl.vm.mousedown = ctrl.vm.mousemove = [ev.clientX, ev.clientY];
        requestAnimationFrame(() => render(ev.target as HTMLCanvasElement, ctrl, true));
      },
      wheel(ev) {
        ev.preventDefault();
        let zoom = Math.pow(3 / 2, -ev.deltaY / 100);
        if (ctrl.vm.transform.scale * zoom < 5) zoom = 5 / ctrl.vm.transform.scale;
        if (ctrl.vm.transform.scale * zoom > 100) zoom = 100 / ctrl.vm.transform.scale;
        ctrl.vm.transform = {
          x: ev.offsetX + (ctrl.vm.transform.x - ev.offsetX) * zoom,
          y: ev.offsetY + (ctrl.vm.transform.y - ev.offsetY) * zoom,
          scale: ctrl.vm.transform.scale * zoom,
        };
        requestAnimationFrame(() => render(ev.target as HTMLCanvasElement, ctrl));
      },
    }
  });
}

function render(canvas: HTMLCanvasElement, ctrl: MapCtrl, raf = false) {
  const vm = ctrl.vm;
  const ctx = canvas.getContext('2d')!;
  ctx.save();

  // fill background
  ctx.beginPath();
  ctx.fillStyle = '#eee';
  ctx.rect(0, 0, canvas.width, canvas.height);
  ctx.fill();

  const width = canvas.width, height = canvas.height;
  console.log(width, height);

  const transform = ctrl.transform();
  ctx.translate(transform.x, transform.y);
  ctx.scale(transform.scale, transform.scale);

  const ymin = Math.floor(-transform.y / transform.scale);
  const xmin = Math.floor(-transform.x / transform.scale);

  const ymax = ymin + Math.ceil(canvas.height / transform.scale);
  const xmax = xmin + Math.ceil(canvas.width / transform.scale);

  const period = 5;

  // draw grid
  ctx.beginPath();
  ctx.fillStyle = '#ddd';
  for (let y = ymin; y <= ymax; y++) {
    for (let x = xmin + (((xmin + y) % 2) + 2) % 2; x <= xmax; x += 2) {
      ctx.rect(x, y, 1, 1);
    }
  }
  ctx.fill();

  // draw axis
  for (let y = Math.floor(ymin / period) * period; y <= ymax + period; y += period) {
    for (let x = Math.floor(xmin / period) * period; x <= xmax + period; x += period) {
      ctx.beginPath();
      ctx.lineWidth = 0.1;
      ctx.moveTo(x - 1, y);
      ctx.lineTo(x + 1, y);
      ctx.moveTo(x, y - 1);
      ctx.lineTo(x, y + 1);
      ctx.stroke();
    }
  }

  /* ctx.scale(100, 100);
  ctx.rect(0, 0, 1, 1);
  ctx.fillStyle = Math.random() > 0.5 ? 'red' : 'blue';
  ctx.fill(); */
  ctx.restore();
  console.log(canvas.width, canvas.height);

  if (vm.mousedown && raf) requestAnimationFrame(() => render(canvas, ctrl, true));
}
