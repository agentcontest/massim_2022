import { EntityStatus, Pos } from './interfaces';

export function compareEntity(a: EntityStatus, b: EntityStatus): number {
  if (a.team < b.team) return -1;
  else if (a.team > b.team) return 1;

  const suffixA = parseInt(a.name.replace(/^[^\d]*/, ''), 10);
  const suffixB = parseInt(b.name.replace(/^[^\d]*/, ''), 10);
  if (suffixA < suffixB) return -1;
  else if (suffixA > suffixB) return 1;

  if (a.name < b.name) return -1;
  else if (a.name > b.name) return 1;
  else return 0;
}

export function compareNumbered(a: string, b: string): number {
  const firstNumberRegex = /^[A-Za-z_-]*(\d+)/;
  const matchA = a.match(firstNumberRegex);
  const matchB = b.match(firstNumberRegex);
  const idxA = matchA ? parseInt(matchA[1], 10) : -1;
  const idxB = matchB ? parseInt(matchB[1], 10) : -1;
  if (idxA < idxB) return -1;
  else if (idxA > idxB) return 1;

  if (a < b) return -1;
  else if (a > b) return 1;
  else return 0;
}

export function samePos(a: Pos, b: Pos): boolean {
  return a[0] == b[0] && a[1] == b[1];
}

export function taxicab(a: Pos, b: Pos): number {
  return Math.abs(a[0] - b[0]) + Math.abs(a[1] - b[1]);
}
