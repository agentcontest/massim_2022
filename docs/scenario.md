# MASSim Scenario Documentation

## Agents Assemble (2022)

* [Intro](#background-story)
* [Actions](#actions)
* [Percepts](#percepts)
* [Configuration](#configuration)
* [Norms](#norms)
* [Commands](#commands)

### Background Story

In the year 2121, everybody is wondering what has happened during the previous 33 years. While the landscape has drastically changed, the citizens of Mars have adapted and specialised their ATPs, e.g. to be more efficient at scouting the environment or clearing debris.
Suddenly, an official regulation agent of the New Official Regulation and Monitoring Syndicate (N.O.R.M.S.) arrives. The agent claims to have been instituted to oversee and regulate the reconstruction of Mars infrastructure to keep everyone safe.

### Introduction

The scenario consists of two __teams__ of agents moving on a grid. The goal is to explore the world and acquire blocks to assemble them into complex patterns.

Agents can adopt roles that are each better suited for specific tasks.
The main goal of the agents is to acquire and _attach_ blocks to each of their four sides, _moving_ and _rotating_ them into specific positions, _connecting_ them together with other agents to create more complex structures, and finally to deliver these structures to specific locations.

__Tournament points__ are distributed according to the score of a team at the end of the simulation. Winning a simulation awards 3 points, while a draw results in 1 point for each team.

## Environment

The environment is a rectangular grid. The dimensions are not known to the agents. Agents only perceive positions relative to their own. The x-axis goes from left to right (or eastwards) and the y-axis from top to bottom (southwards).

The grid loops horizontally and vertically, i.e. if an agent moves off the right "edge" of the map, it will appear on the left side.

Each cell of the grid contains up to one thing that may collide with other things, i.e. agents, blocks and obstacles. Only in the beginning of the simulation, an agent shares *the same* cell with one agent of the other team/s (to ensure fairness). Once one of the agents has moved away, they cannot overlap again later.

### Entity/Agent

Each agent controls one entity in the simulation (s.t. we can use both terms interchangeably). Agents do not know their absolute positioning in the environment. They only know their current *role*, their *energy* level and whether they are currently deactivated. In addition, they perceive all "things" within their vision radius.

#### Energy

Each agent starts with the same energy level and automatically recharges a fixed amount per step (usually 1 energy point). There are various ways for an agent to lose energy (e.g. being hit by clear actions/events, violating norms, using the clear action, ...). Once the energy reaches 0, the agent gets deactivated for a fixed number of steps.

#### Deactivated agents

If an agent becomes deactivated, it **loses all of its attachments** and remains inactive for a fixed configurable number of steps. For as long as the agent is deactivated, all its actions will result in *failed_status*.
The energy value remains 0 during the deactivation period, after which the agent gains a certain (preconfigured) energy level.

#### Vision range (distance for perceiving)

Agents perceive everything in a specific radius r around them. In other words, a cell can be seen by an agent, if its distance (Manhattan distance, taxicab metric, ...) to the agent is at most r. The following illustrates a few different values for r. (X and * can be perceived, X is the agent's position).


```
  r=3       r=4         r=5
   *         *           *
  ***       ***         ***
 *****     *****       *****
***X***   *******     *******
 *****   ****X****   ********* 
  ***     *******   *****X*****
   *       *****     *********
            ***       *******
             *         *****
                        ***
                         *
```


### Things

There are number of __things__ that can inhabit a cell of the grid:

* __Entities__: Each agent controls an entity on the grid. Entities can move around and (with one of the correct roles) attach themselves to other things.
* __Blocks__: The building blocks of the scenario. Each block has a specific type. Agents can pick up blocks and stick multiple blocks together. Blocks have to be arranged into specific patterns to get score points.
* __Obstacles__: Obstacles block the passage of agents. They can be attached and moved away or removed by clear actions. In contrast to blocks, they cannot be connected to other obstacles (or even blocks).
* __Dispenser__: Each dispenser can be used to retrieve a specific kind of block.
* __Marker__: A marker *marks* a cell. Markers do not block other things. For now, markers are used to signal incoming _clear events_.

### Zones

A zone has a center and a radius. There are two types of zones in the game.

* __Goal zones__: Agents have to be on a __goal__ cell in order to be allowed to submit a task.
  * A goal zone moves to another location after a certain number of tasks has been submitted inside.
* __Role zones__: Agents have to be on a __role__ cell in order to use the __adopt__ action.
  * Role zones stay the same for the whole simulation.

### Events

The environment randomly generates events.

#### Clear events

When a clear event happens, a certain area is marked. The timing and size of an event is random. Each event can be perceived (i.e. its clear *markers*) for a few steps before it actually occurs. Once it resolves, the marked area is *cleared*:
* The energy of affected agents is reduced to 0 and therefore, they get deactivated.
* All obstacles and blocks affected vanish.
* New obstacles are created randomly around the center of the event (and not necessarily in the previously marked area).

`Config: match.events.*`:

* `chance` - the chance for a clear event to start in any step (in %)
* `radius` - the bounds for the event size
* `warning` - the number of steps the area is marked before the event resolves
* `create` - the bounds for how many obstacles are created (additional to the number of objects destroyed by the event)
* `perimeter` - an additional radius where new obstacles may be created (added to the event's radius)

### Norms

Norms introduce dynamic small changes in the rules of the game. When a norm is in place, an agent must decide whether to follow or violate it. 
In the latter case, the violator is punished with a decrease in its energy level. 
Before policing a norm, the game's officer announces it to all agents.
After a small number of steps, the norm becomes active.
We call an *approved norm* a norm that is either announced or active.
Each norm regulates a specific *subject*, that is, a characteristic of the scenario.
Moreover, a norm regulates either an agent (individual level) or a team (team level).
For instance, at the team level, a norm may state that at most 2 agents may adopt the role constructor. 

`Config: match.regulation.*`:

Regarding the general regulation:
* `simultaneous` - how many norms are allowed to be in the state approved at the same step.
* `chance` - the chance for a norm to be created (in %)
* `subjects` - the subjects a norm may regulate

Regarding each specific subject:
* `name` - It must be one of the following options: 
  * Carry: the agents are prohibited to carry a certain quantity of things
  * Adopt: the teams are prohibited to have more than a specified number of agents adopting a particular role.
* `announcement` - the number of steps of the announcement period
* `duration` - the number of steps the norm stays active after the announcement period is over
* `punishment` - the number of energy points an agent loses in case it violates a norm
* `weight` - a weight of a subject to be chosen. For instance, if subject Carry has weight of 15 and subject RoleIndividual has weight of 15, then each subject has probability of 50% of being selected
* `optional` - subject dependent information to help specifing what a norm should regulate. 
  * Carry: 
    * `quantity` - the number of things an agent may carry
  * Adopt: 
    * `max` - the maximum number of agents that can adopt a given role

## Tasks

Tasks have to be completed to get score/simulation points. They appear randomly during the course of the simulation.

* __name__: an identifier for the task
* __deadline__: the last step in which the task can be submitted
* __reward__: the number of points that can be earned by completing the task
* __requirements__: each requirement describes a block that has to be attached to the agent
  * __x/y__: the coordinates of the block (the agent being (0,0))
  * __type__: the required type of the block

Tasks have to be submitted in goal zones. Each task can be submitted multiple times for as long as it is active.
A task is replaced by a new one when
* it has been submitted a certain number of times (unknown to the agents; submissions of all teams are counted), or
* its deadline is reached before that.

## Actions

In each step, an agent may execute _exactly one_ action. The actions are gathered and executed in random order.

All actions have the same probability to just fail randomly.

Each action has a number of `parameters`. The exact number depends on the type of action. Also, the position of each parameter determines its meaning. Parameters are always string values.

### skip

The agent won't do anything this turn. Always successful (except for random fail).

### move

Moves the agent in the specified directions. If the agent is currently allowed to move more than one cell, multiple directions can be given.

| No  | Parameter | Meaning                                                                  |
|-----|-----------|--------------------------------------------------------------------------|
| 0-* | direction | One of {n,s,e,w}, representing the direction the agent wants to move in. |

| Failure Code     | Reason                                                                  |
|------------------|-------------------------------------------------------------------------|
| failed_parameter | No parameters given or at least one parameter is not a valid direction. |
| failed_path      | The first move was blocked.                                             |
| partial_success  | At least the first step worked but one of the later moves was blocked.  |

### attach

Attaches a thing (friendly entity, block or obstacle) to the agent. The agent has to be directly beside the thing.

| No  | Parameter | Meaning                                                                              |
|-----|-----------|--------------------------------------------------------------------------------------|
| 0   | direction | One of {n,s,e,w}, representing the direction to the thing the agent wants to attach. |

| Failure Code     | Reason                                              |
|------------------|-----------------------------------------------------|
| failed_parameter | Parameter is not a direction.                       |
| failed_target    | There is nothing to attach in the given direction.  |
| failed_blocked   | The thing is already attached to an opponent agent. |
| failed           | The agent already has too many things attached.     |

### detach

Detaches a thing from the agent. Only the connection between the agent and the thing is released.

| No  | Parameter | Meaning                                                                                   |
|-----|-----------|-------------------------------------------------------------------------------------------|
| 0   | direction | One of {n,s,e,w}, representing the direction to the thing the agent wants to detach from. |

| Failure Code     | Reason                                                    |
|------------------|-----------------------------------------------------------|
| failed_parameter | Parameter is not a direction.                             |
| failed_target    | There was no attachment to detach in the given direction. |
| failed           | There was a thing but not attached to the agent.          |

### rotate

Rotates the agent (and all attached things) 90 degrees in the given direction. For each attached thing, its _final position_ after the rotation has to be free.

| No  | Parameter | Meaning                                                                                |
|-----|-----------|----------------------------------------------------------------------------------------|
| 0   | direction | One of {cw, ccw}, representing the rotation direction (clockwise or counterclockwise). |

| Failure Code     | Reason                                                                                                                            |
|------------------|-----------------------------------------------------------------------------------------------------------------------------------|
| failed_parameter | Parameter is not a (rotation) direction.                                                                                          |
| failed           | One of the things attached to the agent cannot rotate to its target position OR the agent is currently attached to another agent. |

### connect

Two agents can use this action to connect blocks attached to them. They have to specify their partner and the block they want to connect. Both blocks are connected (i.e. attached to each other) if they are next to each other and the connection would not violate any other conditions.

#### Example

_agent1_ is on (3,3) and _agent2_ is on (3,7). _agent1_ has a block attached on (3,4) and one attached to that block on (3,5). _agent2_ has a block attached on (3,6). Both agents want to connect their attached blocks, namely those on (3,5) (of _agent1_) and (3,6) (attached to _agent2_).
Then, _agent1_ has to perform `connect(agent2,0,2)`, while _agent2_ has to perform `connect(agent1,0,-1)` in the same step. If both actions succeed, the blocks will be connected and still attached to both agents.

| No  | Parameter | Meaning                                        |
|-----|-----------|------------------------------------------------|
| 0   | agent     | The agent to cooperate with.                   |
| 1/2 | x/y       | The local coordinates of the block to connect. |

| Failure Code     | Reason                                                                                                                                                                                          |
|------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| failed_parameter | First parameter is not an agent of the same team OR x and y cannot be parsed to valid integers.                                                                                                 |
| failed_partner   | The partner's action is not `connect` OR failed randomly OR has wrong parameters.                                                                                                               |
| failed_target    | At least one of the specified blocks is not at the given position or not attached to the agent or already attached to the other agent.                                                          |
| failed           | The given positions are too far apart OR one agent is already attached to the other (or through other blocks), or connecting both blocks would violate the size limit for connected structures. |

### disconnect

Disconnects two attachments (probably blocks) of the agent.

| No  | Parameter   | Meaning                                       |
|-----|-------------|-----------------------------------------------|
| 0/1 | attachment1 | The x/y coordinates of the first attachment.  |
| 2/3 | attachment2 | The x/y coordinates of the second attachment. |

| Failure Code     | Reason                                                                                   |
|------------------|------------------------------------------------------------------------------------------|
| failed_parameter | No valid integer coordinates given.                                                      |
| failed_target    | Target locations aren't attachments of the agent or not attached to each other directly. |

### request

Requests a new block from a dispenser. The agent has to be in a cell adjacent to the dispenser and specify the direction to it.

E.g. if an agent is on (3,3) and a dispenser is on (3,4), the agent can use `request(s)` to make a block appear on (3,4).

| No  | Parameter | Meaning                                                                               |
|-----|-----------|---------------------------------------------------------------------------------------|
| 0   | direction | One of {n,s,e,w}, representing the direction to the position of the dispenser to use. |

| Failure Code     | Reason                                                                   |
|------------------|--------------------------------------------------------------------------|
| failed_parameter | Parameter is not a direction.                                            |
| failed_target    | No dispenser was found in the specific position.                         |
| failed_blocked   | The dispenser's position is currently blocked by another agent or thing. |

### submit

Submit the pattern of things that are attached to the agent to complete a task.

| No  | Parameter | Meaning                     |
|-----|-----------|-----------------------------|
| 0   | task      | The name of an active task. |

| Failure Code  | Reason                                                                                                 |
|---------------|--------------------------------------------------------------------------------------------------------|
| failed_target | No _active_ task could be associated with first parameter, or task has not been accepted by the agent. |
| failed        | One or more of the requested blocks are missing OR the agent is not on a goal terrain.                 |

### clear

Clears a target cell if the agent has sufficient energy.
* The area is cleared of blocks and obstacles.
* The action consumes a fixed amount of energy.
* Targeted entities may get damaged.
  * If the role's maximum clear distance is 1 or less, other entities cannot be damaged.
  * The damage depends on the distance between both entities.
  * Damage results in loss of energy.

The clear action's success rate depends on an agent's role. I.e. there may be an additional probability to receive
the `failed_random` result.

| No  | Parameter | Meaning                                     |
|-----|-----------|---------------------------------------------|
| 0/1 | target    | The x/y coordinates of the target position. |

| Failure Code     | Reason                                                                         |
|------------------|--------------------------------------------------------------------------------|
| failed_parameter | No valid integer coordinates given.                                            |
| failed_target    | Target location is not within the agent's vision radius or outside the grid.   |
| failed_resources | The agent's energy is too low.                                                 |
| failed_location  | The agent is targeting a cell out of reach.                                    |
| failed_random    | The action failed due to random failure or the additional probability to fail. |

* `Config: match.clearSteps` - number of action required for a successful clear
* `Config: match.clearEnergyCost` - energy cost for a clear action (subtracted when the clear actually resolves)

### adopt

Adopts a role if the agent is in a role zone.

| No  | Parameter | Meaning                        |
|-----|-----------|--------------------------------|
| 0   | role      | The name of the role to adopt. |

| Failure Code     | Reason                                              |
|------------------|-----------------------------------------------------|
| failed_parameter | No parameter or parameter is not a valid role name. |
| failed_location  | Agent is not in a role zone.                        |

### survey

Localises or gathers information about a target. Moving targets are harder to survey.

| No  | Parameter | Meaning                                             |
|-----|-----------|-----------------------------------------------------|
| 0   | target    | The target type. One of "dispenser", "goal", "role" |

In this case, the agent will receive a percept with the distance to the nearest dispenser,
goal zone or role zone before the next step.

| Failure Code     | Reason                                      |
|------------------|---------------------------------------------|
| failed_parameter | No target parameter or target is not valid. |
| failed_target    | No instance of the target found.            |


The action can also be used with position parameters:

| No  | Parameter |                                    |
|-----|-----------|------------------------------------|
| 0/1 | target    | The x/y coordinates of the target. |

In this case, the agent will receive information about the agent (name, role and energy) at the given location.

| Failure Code     | Reason                                                                     |
|------------------|----------------------------------------------------------------------------|
| failed_parameter | Parameters are not coordinates or too many parameters given.               |
| failed_location  | The location is outside the agent's vision.                                |
| failed_target    | There is no entity at the given location. It might have moved away before. |

### all actions

All actions can also have the following failure codes:

| Failure Code   | Reason                                               |
|----------------|------------------------------------------------------|
| failed_random  | The action failed randomly.                          |
| failed_status  | The agent is deactivated.                            |
| failed_role    | The agent's current role does not permit the action. |
| unknown_action | The action is not part of the game.                  |

## Percepts

Percepts are sent by the server as JSON files and contain information about the current simulation. Initital percepts (sent via `SIM-START` messages) contain static information while other percepts (sent via `REQUEST-ACTION` messages) contain information about the current simulation state.

The complete JSON format is discussed in [protocol.md](protocol.md).

### Initial percept

This percept contains information that does not change during the whole simulation. As mentioned in the protocol description, everything is contained in a `simulation` element.

Complete Example (with bogus values):

```JSON
{
  "type": "sim-start",
  "content": {
    "time": 1556638383203,
    "percept": {
      "name": "agentA1",
      "team": "A",
      "teamSize": 15,
      "steps": 700,
      "roles": [
        {
          "name": "SampleRole",
          "vision": 5,
          "actions": ["move", "attach"],
          "speed": [2,1,0],
          "clear": {
            "chance": 0.5,
            "maxDistance": 1 
          }
        },
        {"..." :  "..."}
      ]
    }
  }
}
```

* __name__: the agent's name
* __team__: the name of the agent's team
* __teamSize__: number of agents in the agent's team in the current round
* __steps__: the sim's total number of steps
* __roles__: all roles in the simulation
  * __name__: name of the role
  * __vision__: vision range
  * __actions__: actions available to the role
  * __speed__: the different speeds the agent can move at with things attached
    * e.g. for [2,1,0], the agent can move 2 cells with no things attached, 1 cell with one thing attached and 0 cells (i.e. not at all) with two or more things attached.
  * __clear__: properties of the clear action
    * __chance__: the probability of the action to succeed (between 0 and 1)
    * __maxDistance__: the maximum range of the action (if 1, entities cannot be damaged)

### Step percept

This percept contains information about the simulation state at the beginning of each step.

Agents perceive the state of a cell depending on their vision. E.g. if they have a vision of 5, they can sense all cells that are up to 5 steps away.

Example (complete request-action message):

```json
{
   "type": "request-action",
   "content": {
      "id": 1,
      "time": 1556636930397,
      "deadline": 1556636934400,
      "step" : 1,
      "percept": {
         "score": 0,
         "lastAction": "move",
         "lastActionResult": "success",
         "lastActionParams": ["n"],
         "energy": 277,
         "deactivated": false,
         "role": "SampleRole",
         "things": [
            {
               "x": 0,
               "y": 0,
               "details": "",
               "type": "entity"
            },
            {
               "x": 0,
               "y": -5,
               "details": "",
               "type": "obstacle"
            },
            {
               "x": 2,
               "y": -1,
               "details": "b1",
               "type": "block"
            },
            {
               "x": 2,
               "y": -1,
               "type": "marker",
               "details" : "clear"
            },
            {
               "x": 3,
               "y": 4,
               "details": "",
               "type": "taskboard"
            }
         ],
         "goalZones": [[1,1],[1,2],[4,0]],
         "roleZones": [[0,4]],
         "events": [

         ],
         "tasks": [
          {
              "name": "task2",
              "deadline": 188,
              "reward" : 44,
              "requirements": [
                  {
                     "x": 1,
                     "y": 1,
                     "details": "",
                     "type": "b0"
                  },
                  {
                     "x": 0,
                     "y": 1,
                     "details": "",
                     "type": "b1"
                  },
                  {
                     "x": 0,
                     "y": 2,
                     "details": "",
                     "type": "b1"
                  }
               ]
            }
         ],
         "norms": [
            {
              "name": "n1",
              "start": 1,
              "until" : 100,
              "level" : "individual",
              "requirements": [
                  {
                    "type": "carry",
                    "name": "any",
                    "quantity": 1
                  }
               ],
              "punishment" : 15
            }
         ],
         "violations": ["n1"],
         "attached": [[2,-1]]
      }
   }
}
```

(Note: as this is a JSONObject, the order of keys is not guaranteed.)

* __score__: the current team score
* __lastAction__: the last action submitted by the agent
* __lastActionResult__: the result of that action
* __lastActionParams__: the parameters of that action
* __energy__: the agent's current energy level
* __deactivated__: whether the agent is deactivated
* __task__: the most recently accepted task (by the agent)
* __things__: things in the simulation visible to the agent
  * __x/y__: position of the thing _relative_ to the agent
  * __type__: the type of the thing (entity, block, dispenser, marker,...)
  * __details__: details about the thing
    * for blocks and dispensers: the block type
    * for entities: the team
    * for obstacles: empty
    * for markers: the type of marker (i.e. clear)
      * _clear_: the cell is about to be cleared
      * _ci_: "clear_immediate" - a clear event will clear the cell in 2 steps or less
      * _cp_: "clear_perimeter" - the cell is in the perimeter of a clear event (i.e. new obstacles may be generated there as part of the event)
* __goalZones__: an array of positions, each of which is part of a goal zone
* __roleZones__: an array of positions, each of which is part of a role zone
* __events__: an array of incidents during the previous step; each event has a type and additional info. (These events do not include the map events, like e.g. clear events.)
  * __type__: one of ["surveyed", "hit"]
    * if __surveyed__:
      * __target__: one of ["goal", "role", "agent", "dispenser"]
        * if __role__, __goal__, or __dispenser__:
          * __distance__: distance to the nearest instance of the surveyed type
        * if __agent__:
          * __name__: name of the agent
          * __role__: current role of the agent
          * __energy__: current energy of the agent
    * if __hit__:
      * __origin__: the position where the damage came from (might be off if the agent moved during the previous step)
* __task__: a task that is currently active
  * __name__: the task's identifier
  * __start__: the first step during which the task can be completed
  * __reward__: the score points rewarded for completing the job
  * __requirements__: the relative positions in which blocks have to be attached to the agent (the agent being (0,0))
    * __x/y__: the relative position of the required block
    * __type__: the type of the required block
    * __details__: currently not used
* __norm__: a norm that is currently approved
  * __name__: the norm's identifier
  * __start__: the step in which a norm becomes active
  * __until__: the step in which a norm becomes inactive
  * __level__: whether the norm applies to individual agents or a team of agents
  * __requirements__: what the norm regulates
    * __type__: the subject of the norm
    * __name__: the precise name the subject refers to, e.g., the role *constructor*
    * __quantity__: the maximum quantity that can be carried/adopted
* __violations__: the list of norms an agent is violating at the current step
* __attached__: an array of positions - each position represents a thing that is (directly or indirectly) attached to an entity

## Configuration

Each simulation configuration is one object in the `match` array.

Example:

```JSON
{
  "id": "2022-SampleSimulation",
  "steps": 800,
  "randomSeed": 17,
  "randomFail": 1,
  "entities" : [
    {"standard": 15}
  ],
  "clusterBounds" : [1,3],

  "roles" : [
    {
      "name": "worker",
      "vision": 5,
      "actions": ["skip", "move", "rotate", "adopt", "request", "attach", "detach", "connect", "disconnect", "submit"],
      "speed": [1, 1, 0]
    },
    {
      "..." : "..."
    }
  ],

  "clearEnergyCost" : 2,
  "deactivatedDuration" : 10,
  "maxEnergy" : 100,
  "refreshEnergy" : 50,
  "stepRecharge" : 1,
  "clearDamage" : [32, 16, 8, 4, 2, 1],

  "attachLimit" : 10,

  "grid" : {
    "height" : 50,
    "width" : 50,
    "file" : "conf/maps/test40x40.bmp",
    "instructions": [
      ["cave", 0.45, 10, 5, 4],
      ["line-border", 1],
      ["ragged-border", 3]
    ],
    "goals": {
      "number" : 3,
      "size" : [1,3],
      "moveProbability": 0.1
    },
    "roleZones" : {
      "number" : 5,
      "size" : [3, 5]
    }
  },

  "blockTypes" : [3, 3],
  "dispensers" : [5, 10],

  "tasks" : {
    "size" : [1, 4],
    "concurrent" : 2,
    "iterations" : [5, 10],
    "maxDuration" : [100, 200]
  },

  "events" : {
    "chance" : 15,
    "radius" : [3, 5],
    "warning" : 5,
    "create" : [-3, 1],
    "perimeter" : 2
  },

  "regulation" : {
    "simultaneous" : 1,
    "chance": 15,
    "subjects" : [
        {
            "name" : "Carry",
            "announcement" : [10, 20],
            "duration" : [100, 200],
            "punishment": [10, 20],
            "weight": 15,
            "optional": {
                "quantity": [1,1]
            }
        },
        {
          "...": "..."
        }
    ]
  },

  "setup" : "conf/setup/test.txt"
}
```

For each simulation, the following parameters may be specified:

* __id__: a name for the simulation; e.g. used in replays together with the starting time
* __steps__: the number of steps the simulation will take
* __randomSeed__: the random seed that is used for map generation and action execution
* __randomFail__: the probability for any action to fail (in %)
* __entities__: the number of entities (i.e. agents) per type
* __clusterBounds__: min./max. number of agents starting near each other
* __roles__: (see below the list for more information)
  * __name__: .
  * __vision__: max. distance the agent can perceive at
  * __actions__: all actions the role is allowed to use
  * __speed__: the different speeds the agent can move at with things attached
    * e.g. for [2,1,0], the agent can move 2 cells with no things attached, 1 cell with one thing attached and 0 cells (i.e. not at all) with two or more things attached.
  * __clear__: properties of the clear action
    * __chance__: the probability of the action to succeed (between 0 and 1)
    * __maxDistance__: the maximum range of the action (if 1, entities cannot be damaged)
* __clearEnergyCost__: how much an effective clear action costs
* __deactivatedDuration__: for how many steps an agent remains disabled
* __maxEnergy__: an agent's initial and maximum energy level
* __refreshEnergy__: how much energy an agent is restored with after a deactivation period 
* __stepRecharge__: how much energy each agent recharges per step
* __clearDamage__ : similar to speed, the value at index i determines the damage a target at distance i receives (e.g. the value at index 0 would be self-inflicted damage, the value at index 1 dealt to a target directly adjacent to the agent etc.). If a distance longer than the list is required, the last value of the list is used. E.g. the list [7] would imply any target receives 7 damage regardless of distance.
* __attachLimit__: the maximum number of things that can be attached to each other
* __blockTypes__: upper and lower bounds for the number of block types
* __dispensers__: upper and lower bounds for the number of dispensers per block type
* __grid__:
  * __height/width__: dimensions of the environment
  * __file__: a bitmap file describing the map layout (see examples for more information)
  * __instructions__: an arbitrary number of map generation steps
    * __cave__: generates a cave like structure using a cellular automaton
      * 1st parameter: chance for a cell to start as an obstacle
      * 2nd parameter: number of iterations
      * 3rd parameter: min. number of obstacle neighbours for an empty cell to become an obstacle
      * 4th parameter: min. number of obstacle neighbours for an obstacle to remain an obstacle
    * __line-border__: creates a straight line of obstacles around the map
      * 1st parameter: width of the line
    * __ragged-border__: creates an irregular border around the map
      * 1st parameter: initial (and average) width of the border
  * __goals__:
    * __number__: number of goal areas
    * __size__: bounds for goal area radius
    * __moveProbability__: probability (0-1) for a goal zone to move after a task has been submitted inside
  * __roleZones__:
    * __number__: how many role zones to place
    * __size__: bounds for how big each role zone can be
* __tasks__:
  * __size__: bounds for the size of a tasks (i.e. number of blocks)
  * __maxDuration__: bounds for a task's (maximum) duration (i.e. number of steps)
  * __concurrent__: how many tasks should exist at any given time
  * __iterations__: bounds for often a task can be submitted before it is replaced
* __regulation__: see [Norms](#norms)
* __events__:
  * __chance__: chance to generate an event in any step (0-100)
  * __radius__: bounds for the event radius
  * __warning__: number of steps the event area is marked before the event occurs
  * __create__: bounds for how many additional obstacles an event can create (besides those that were removed)
  * __perimeter__: an additional radius where new obstacles may be created (added to the event's radius)
* __setup__: a file describing additional steps to be performed before the simulation starts
  * might be useful for testing & debugging
  * see examples for more information

Currently, there is only one standard agent role.

Roles: Only the first role has to be configured with all values. This role will be the default role. If some
value for another role is not set, the value/s of the default role will be used. Each role also
gets all actions of the default role.

## Commands

Currently, no special scenario commands are available. You may use a simulation [setup file](#configuration) instead.
