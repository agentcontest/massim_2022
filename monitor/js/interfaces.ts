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
  entities: Agent[];
  blocks: Block[];
  dispensers: Dispenser[];
  tasks: Task[];
  clear: ClearEvent[];
  obstacles: Obstacle[];
  scores: { [team: string]: number };
}

export type Pos = [number, number];

export interface Positioned {
  position: Pos;
}

export interface Obstacle extends Positioned {}

export interface AgentStatus {
  name: string;
  team: string;
  action: string; // can be empty string before first step
  actionResult: string;
}

export interface Agent extends Positioned, AgentStatus {
  id: number;
  energy: number;
  vision: number;
  attached?: Pos[];
  disabled?: boolean;
  actionParams: string[];
  acceptedTask?: string; // can be empty string before first accept
}

export interface Block extends Positioned {
  type: BlockType;
  attached?: Pos[];
}

export interface Dispenser extends Positioned {
  id: number;
  type: BlockType;
}

export interface Task {
  reward: number;
  name: string;
  deadline: number;
  requirements: Block[];
}

export interface ClearEvent extends Positioned {
  radius: number;
}

export interface Rect {
  x1: number;
  x2: number;
  y1: number;
  y2: number;
  width: number;
  height: number;
}
