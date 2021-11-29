import { h, VNode } from 'snabbdom';

import { Ctrl } from './ctrl';
import { overlay } from './overlay';
import { MapCtrl, mapView } from './map';
import * as styles from './styles';

export function view(ctrl: Ctrl): VNode {
  return h('div#monitor', [ctrl.maps.length ? agentView(ctrl) : mapView(ctrl.map), overlay(ctrl)]);
}

function agentView(ctrl: Ctrl): VNode | undefined {
  if (!ctrl.vm.static) return;

  return h(
    'div.maps',
    ctrl.maps.map(m => {
      const entity = m.selectedEntity();
      if (!entity) return;

      const violations = ctrl.vm.dynamic?.violations.filter(v => v.who == entity.name).map(v => v.norm) || [];

      return h(
        'div',
        {
          class:
            entity.action && entity.actionResult
              ? {
                  map: true,
                  [entity.action]: true,
                  [entity.actionResult]: true,
                }
              : {
                  map: true,
                },
        },
        [
          h(
            'a.team',
            {
              style:
                m.vm.selected === ctrl.map.vm.selected
                  ? {
                      background: 'white',
                      color: 'black',
                    }
                  : styles.team(ctrl.vm.teamNames.indexOf(entity.team)),
              on: {
                click() {
                  ctrl.map.vm.selected = entity.id;
                  ctrl.toggleMaps();
                },
              },
            },
            `${entity.name} (${entity.pos[0]}|${entity.pos[1]})`
          ),
          mapView(m, {
            size: 250,
            viewOnly: true,
          }),
          h('div.meta', [
            h('div', `role = ${entity.role}, energy = ${entity.energy}` + (entity.deactivated ? ' 🪫' : '')),
            entity.action ? h('div', `${entity.action}(…) = ${entity.actionResult}`) : undefined,
            violations.length ? h('div', 'violates ' + violations.join(', ') + ' ⚠️') : undefined,
          ]),
        ]
      );
    })
  );
}
