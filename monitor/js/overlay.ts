import { Entity, StaticWorld, DynamicWorld, Task, Pos } from './interfaces';
import { Ctrl, ReplayCtrl } from './ctrl';
import { drawBlocks, drawEntity } from './map';
import { samePos, taxicab } from './util';
import * as styles from './styles';

import { h, VNode } from 'snabbdom';

function replay(ctrl: ReplayCtrl) {
  return h('div.box.replay', [
    h('div', [h('strong', 'Replay:'), ' ', ctrl.name()]),
    h('div', [
      h('button', { on: { click: () => ctrl.setStep(-1) } }, '|<<'),
      h('button', { on: { click: () => ctrl.setStep(ctrl.step - 10) } }, '<<'),
      h(
        'button',
        {
          on: { click: () => ctrl.toggle() },
        },
        ctrl.playing() ? '||' : '>'
      ),
      h('button', { on: { click: () => ctrl.setStep(ctrl.step + 10) } }, '>>'),
      h('button', { on: { click: () => ctrl.setStep(99999999) } }, '>>|'),
    ]),
  ]);
}

function simplePlural(n: number, singular: string): string {
  if (n === 1) return '1 ' + singular;
  else return n + ' ' + singular + 's';
}

function teams(teamNames: string[], world: DynamicWorld): VNode[] {
  return world.scores.map(([name, score]) =>
    h('div.team', { style: styles.team(teamNames.indexOf(name)) }, `${name}: $${score}`)
  );
}

function tasks(ctrl: Ctrl, st: StaticWorld, world: DynamicWorld): VNode[] {
  return world.tasks.map(task => taskDetails(ctrl, st, world, task));
}

function norms(ctrl: Ctrl, world: DynamicWorld): VNode {
  return h(
    'ul',
    world.norms.map(norm => {
      const who = norm.level === 'individual' ? 'each agent' : 'a team';
      const what = norm.requirements.map(requirement => {
        if (requirement.type === 'BLOCK')
          return h('li', `carry at most ${requirement.quantity} of ${requirement.name}`);
        else if (requirement.type === 'ROLE')
          return h('li', `have at most ${requirement.quantity} ${requirement.name} agents`);
        else return h('li', `undefined`);
      });
      return h('li', [
        h('strong', norm.name),
        ` from ${norm.start} to ${norm.until}: ${who} must`,
        h('ul', what),
        `or lose ${norm.punishment} energy`,
      ]);
    })
  );
}

function hover(ctrl: Ctrl, st: StaticWorld, world: DynamicWorld, pos: Pos): VNode | undefined {
  // pos
  const r = [h('li', `x = ${pos[0]}, y = ${pos[1]}`)];

  // obstacles
  for (const obstacle of world.obstacles) {
    if (samePos(obstacle.pos, pos)) {
      r.push(h('li', h('span', { style: { background: styles.obstacle, color: 'white' } }, 'obstacle')));
    }
  }

  // goal zones
  for (const zone of world.goalZones) {
    if (taxicab(zone.pos, pos) <= zone.r) {
      r.push(h('li', h('span', { style: { background: styles.goalZoneOnLight, color: 'white' } }, 'goal zone')));
    }
  }

  // role zones
  for (const zone of world.roleZones) {
    if (taxicab(zone.pos, pos) <= zone.r) {
      r.push(h('li', h('span', { style: { background: styles.roleZoneOnLight, color: 'white' } }, 'role zone')));
    }
  }

  // dispensers
  for (const dispenser of world.dispensers) {
    if (samePos(dispenser.pos, pos)) {
      r.push(h('li', ['dispenser: type = ', blockSpan(st, dispenser.type)]));
    }
  }

  // blocks
  for (const block of world.blocks) {
    if (samePos(block.pos, pos)) {
      r.push(h('li', ['block: type = ', blockSpan(st, block.type)]));
    }
  }

  // entities
  for (const entity of world.entities) {
    if (samePos(entity.pos, pos)) {
      r.push(h('li', ['entity: ', ...entityDescription(ctrl, entity)]));
    }
  }

  return h('ul', r);
}

function blockSpan(st: StaticWorld, type: string): VNode {
  return h(
    'span',
    {
      style: {
        background: styles.blocks[st.blockTypes.indexOf(type)],
        color: 'white',
      },
    },
    type
  );
}

function entityDescription(ctrl: Ctrl, entity: Entity): Array<VNode | string> {
  const r = [
    'name = ',
    h(
      'span',
      {
        style: styles.team(ctrl.vm.teamNames.indexOf(entity.team)),
      },
      entity.name
    ),
    `, role = ${entity.role}, energy = ${entity.energy}`,
  ];
  if (entity.action && entity.actionResult)
    r.push(
      ', ',
      h(
        'span',
        {
          class: {
            [entity.action]: true,
            [entity.actionResult]: true,
          },
        },
        `${entity.action}(…) = ${entity.actionResult}`
      )
    );
  if (entity.attached?.length) r.push(`, ${entity.attached.length}\xa0attached`);
  if (entity.deactivated) r.push(', deactivated');
  return r;
}

function taskDetails(ctrl: Ctrl, st: StaticWorld, dynamic: DynamicWorld, task: Task): VNode {
  const xs = task.requirements.map(b => Math.abs(b.pos[0]));
  const ys = task.requirements.map(b => Math.abs(b.pos[1]));
  const yMin = Math.min(0, ...ys);
  const width = 2 * Math.max(0, ...xs) + 1;
  const height = Math.max(0, ...ys) + 1 - yMin;
  const elementWidth = 218;
  const gridSize = Math.min(Math.floor(elementWidth / width), 35);
  const elementHeight = gridSize * height;
  const render = function (vnode: VNode) {
    const canvas = vnode.elm as HTMLCanvasElement;
    const ctx = canvas.getContext('2d')!;
    ctx.save();
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    ctx.translate((elementWidth - gridSize) / 2, 0);
    ctx.scale(gridSize, gridSize);
    ctx.translate(0, -yMin);
    drawEntity(ctx, 0, 0, { pos: [0, 0] }, 0);
    drawBlocks(ctx, 0, 0, st, task.requirements);
    ctx.restore();
  };
  return h('div.box', [
    h(
      'p',
      `$${task.reward} for ${task.name} until step ${task.deadline} (${simplePlural(
        task.requirements.length,
        'block'
      )})`
    ),
    h('canvas', {
      attrs: {
        width: elementWidth,
        height: elementHeight,
      },
      hook: {
        insert: render,
        update: (_, vnode) => render(vnode),
      },
    }),
  ]);
}

function disconnected(): VNode {
  return h('div.box', [
    h('p', 'Live server not connected.'),
    h(
      'a',
      {
        attrs: { href: document.location.pathname + document.location.search },
      },
      'Retry now.'
    ),
  ]);
}

function box(child: VNode | undefined): VNode | undefined {
  return child ? h('div.box', child) : undefined;
}

export function overlay(ctrl: Ctrl): VNode {
  const selectedEntity = ctrl.map.selectedEntity();
  return h('div#overlay', [
    ctrl.vm.static && (ctrl.replay ? replay(ctrl.replay) : h('div.box', ctrl.vm.static.sim)),
    ctrl.vm.state === 'error' || ctrl.vm.state === 'offline'
      ? ctrl.replay
        ? h('div.box', ctrl.vm.static ? 'Could not load step' : 'Could not load replay')
        : disconnected()
      : undefined,
    ctrl.vm.static && ctrl.vm.dynamic
      ? h('div.box', [`Step: ${ctrl.vm.dynamic.step} / ${ctrl.vm.static.steps - 1}`])
      : undefined,
    ctrl.vm.state === 'connecting' ? h('div.box', ['Connecting ...', h('div.loader')]) : undefined,
    ctrl.vm.state === 'online' && (!ctrl.vm.static || !ctrl.vm.dynamic)
      ? h('div.box', ['Waiting ...', h('div.loader')])
      : undefined,
    ...(ctrl.vm.state === 'online' && ctrl.vm.static && ctrl.vm.dynamic
      ? [
          h('div.box', teams(ctrl.vm.teamNames, ctrl.vm.dynamic)),
          h('div.box', [
            h(
              'button',
              {
                on: {
                  click: () => ctrl.toggleMaps(),
                },
              },
              ctrl.maps.length ? 'Global view' : 'Entity view'
            ),
            ctrl.maps.length
              ? undefined
              : h(
                  'button',
                  {
                    on: {
                      click() {
                        ctrl.resetTransform();
                        ctrl.redraw();
                      },
                    },
                  },
                  'Reset zoom'
                ),
          ]),
          selectedEntity ? box(h('div', ['Selected entity: ', ...entityDescription(ctrl, selectedEntity)])) : undefined,
          ...tasks(ctrl, ctrl.vm.static, ctrl.vm.dynamic),
          h('div.box', norms(ctrl, ctrl.vm.dynamic)),
          ctrl.vm.hover ? box(hover(ctrl, ctrl.vm.static, ctrl.vm.dynamic, ctrl.vm.hover)) : undefined,
        ]
      : []),
  ]);
}
