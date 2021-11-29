export type Redraw = () => void;

export type ConnectionState = 'offline' | 'online' | 'connecting' | 'error';

export type BlockType = string;

export interface StaticWorld {
  sim: string;
  grid: Grid;
  teams: { [key: string]: Team };
  blockTypes: BlockType[];
  steps: number;
}

export interface Team {
  name: string;
}

export interface Grid {
  width: number;
  height: number;
}

export interface DynamicWorld {
  step: number;
  obstacles: Obstacle[];
  goalZones: GoalZone[];
  entities: Agent[];
  blocks: Block[];
  dispensers: Dispenser[];
  tasks: Task[];
  clear: ClearEvent[];
  scores: { [team: string]: number };
}

export type Pos = [number, number];

export interface Positionable {
  pos: Pos;
}

export interface Obstacle extends Positionable {}

export interface GoalZone extends Positionable {
  r: number;
}

export interface AgentStatus {
  name: string;
  team: string;
  action: string; // can be empty string before first step
  actionResult: string;
}

export interface Agent extends Positionable, AgentStatus {
  id: number;
  energy: number;
  vision: number;
  attached?: Pos[];
  disabled?: boolean;
  actionParams: string[];
  acceptedTask?: string; // can be empty string before first accept
}

export interface Block extends Positionable {
  type: BlockType;
  attached?: Pos[];
}

export interface Dispenser extends Positionable {
  id: number;
  type: BlockType;
}

export interface Task {
  reward: number;
  name: string;
  deadline: number;
  requirements: Block[];
}

export interface ClearEvent extends Positionable {
  r: number;
}

export interface Rect {
  x1: number;
  x2: number;
  y1: number;
  y2: number;
  width: number;
  height: number;
}
