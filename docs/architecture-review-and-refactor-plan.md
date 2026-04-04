# Ages Of Siege Architecture Review And Refactor Plan

## Purpose

This review covers the two active codebases that make up the current Ages Of Siege gameplay stack:

- `C:\Users\Stamp\OneDrive\Documents\Ages Of Siege Control Mod`
- `C:\Users\Stamp\OneDrive\Documents\_tmp_age_of_siege_traders`

The goal of this review is not only to describe the current code, but to turn that understanding into a realistic refactor program that reduces hardcoding, clarifies ownership, improves reuse, and prepares both mods for expansion.

This document assumes the long-term product direction includes:

- more ages
- more siege types and runtime behaviors
- more defender archetypes
- more trader categories
- more quest types
- more bank and treasury interactions
- more full-screen and handled UI surfaces
- more companion mods later if needed

## Executive Summary

The current architecture is workable but uneven.

The strongest parts are:

- the control mod already has a real domain model for siege sessions, plans, and persistent campaign state
- the traders mod already has a meaningful domain split between economy, quests, screens, and NPCs
- both mods already share a common fantasy progression idea: age, treasury, settlement growth, and defense pressure

The weakest parts are:

- the cross-mod contract is stringly typed and built on raw object-share lookups plus `NbtCompound` payloads
- too much important data is embedded in code constants and hardcoded registries
- the UI system is visually converging but structurally fragmented
- state ownership is too centralized in `SiegeBaseState`
- AI/runtime logic is still controller-heavy and policy-light
- old compatibility shims and transitional patterns remain in the hot path

Performance will not be meaningfully improved by merging the two mods into one jar.
The real gains will come from:

- reducing repeated entity scans and broad tick work
- decomposing orchestration logic
- externalizing tuning/content
- reducing state mutation churn
- clarifying contracts and ownership

Recommendation:

- keep the mods separate
- treat the control mod as the authoritative warfare/campaign platform
- refactor the mod boundary into a typed contract
- move toward a logical three-layer architecture even if it remains physically two repos for now

## Current System Overview

## Control Mod: What It Does Today

### High-level role

`Ages Of Siege Control Mod` is the authoritative gameplay runtime.

It currently owns:

- settlement claiming
- siege campaign progression
- persistent settlement and siege state
- defender tokens, recall, and runtime post enforcement
- siege planning, spawning, phase transitions, and outcome resolution
- treasury state and tracked bank defense state
- full-screen command/report/workbench screens
- the integration API consumed by traders

### Main package map

- `api`: cross-mod integration contract surface
- `block`: work bench, repair chest, block registration
- `command`: debug/admin commands
- `defense`: defender tokens, recall, runtime AI, token progression
- `entity`: siege ram and entity registration
- `item`: settlement and rally markers, ledger item, token items
- `ledger`: army ledger screen, packet service, snapshot model
- `report`: war report screen, report service, report snapshots
- `siege`: catalogs, definitions, director, old manager, exporters
- `siege.runtime`: session/plan/phase/order/runtime DTOs
- `siege.service`: planner, spawner, unit controller, observation, rewards, wall/objective services
- `state`: persistent world state including settlement, treasury, placed defenders
- `workbench`: army work bench screen, service, snapshot

### Runtime flow

Current top-level flow:

1. bootstrap registers entities, blocks, items, catalogs, API, services, commands, and runtime tick handlers
2. player claims a settlement with the standard and chooses a rally point
3. player deploys defenders through token items
4. player interacts through the Army Ledger and Work Bench
5. `SiegeDirector` stages and runs sieges using `SiegeBaseState`
6. runtime services observe battlefield state, refresh plans, dispatch units, and resolve outcomes
7. persistent state is written through `PersistentState` storage
8. traders mod reads age/treasury/bank information through the integration API

### Key strengths

- explicit siege session model already exists
- phase-driven direction is better than many boolean flags
- defenders already use token persistence rather than pure summon commands
- full-screen command UI is a good product direction
- bank protection has already started to become part of the warfare loop

### Key structural weaknesses

- `SiegeBaseState` mixes settlement state, campaign progression, runtime session state, defender roster, wall damage, tracked bank state, migration logic, and compatibility shims
- `ArmyLedgerService` combines packet wiring, snapshot assembly, command handling, preview logic, and defender mutation
- UI screens do too much of their own layout/render/control work directly
- runtime controllers still depend on many hardcoded constants
- old and new siege architectures coexist

## Traders Mod: What It Does Today

### High-level role

`_tmp_age_of_siege_traders` is the economy and social progression layer.

It currently owns:

- trader blocks and NPCs
- bank and trader handled screens
- guild progression
- trader stock selection and pricing
- quest offering, acceptance, and claim flow
- caravan unlock logic
- coin items and bank deposit behaviors

### Main package map

- `bank`: thin bank state wrapper over the control-mod treasury API
- `block`: trader/bank/waystone blocks
- `command`: trader commands
- `economy`: access adapter, offer registry, age snapshot, valuation, treasury milestones
- `entity`: trader NPC entity, renderers, screen factories
- `item`: coins, quest journal, trader items
- `progression`: guild reputation and unlock logic
- `quest`: quest registry, manager, hooks, journal sync, quest state
- `screen`: handled screens, handlers, shared trader UI theme/layout
- `world`: camp builder

### Runtime flow

Current top-level flow:

1. bootstrap registers screen handlers, entities, blocks, items, quest hooks, and commands
2. trader NPCs and bank blocks open handled screens
3. bank and trader screen handlers query treasury and age state through `AgeOfSiegeAccess`
4. quests are generated and tracked in trader-owned persistent state
5. guild progression uses quest completions and trade value to unlock more offers, discounts, and rare caravan states
6. quests also infer siege-related targets from control-mod entity IDs and command tags

### Key strengths

- trader UI code already has the beginnings of a shared handled-screen base
- guild progression and treasury unlocks already create a satisfying tie-in to the siege loop
- trader quest flow is already meaningful enough to become a major mid-game progression system

### Key structural weaknesses

- `TraderOfferRegistry` and `QuestRegistry` are fully code-hardcoded content catalogs
- `AgeOfSiegeAccess` depends on string constants and raw API payload reconstruction
- quest target matching uses hardcoded IDs and tags that are fragile across runtime changes
- `TraderScreenHandler` mixes catalog lookup, paging, display formatting metadata, buy/sell logic, guild progression, and bank state reads
- `QuestHooks` performs per-player per-tick refresh and also injects reward/status-effect behavior

## Why The Two Mods Are Separate

This split makes sense today and should remain.

The separation matches two different domains:

- warfare/campaign runtime
- economy/trader/social progression

Benefits of keeping them separate:

- lower blast radius when iterating on trader systems
- easier future addition of more companion mods
- clearer pack-level modularity
- the control mod can evolve into the authoritative platform/API

What separation is currently costing:

- weakly typed integration
- duplicated progression concepts
- inconsistent UI foundations
- implicit coupling through IDs, strings, and assumptions

This is a contract-design problem, not a modularity problem.

## Why Merging Them Into One Mod Is Not The Best Fix

Merging would not materially improve the expensive parts of the game.

The main costs today are:

- AI tick work
- pathing and planner scans
- entity queries
- snapshot assembly
- large screens doing their own layout/render work
- state mutation and persistence patterns

Those costs do not disappear because two jars become one.

A merge might simplify packaging and short-term iteration, but it would also:

- make ownership less clear
- increase monolith coupling
- reduce future reuse of traders as a separate feature layer
- make future companion modules harder

Recommended stance:

- do not merge for performance reasons
- refactor the boundary instead

## Current Inter-Mod Linkage

## What currently links the mods

The main integration seam is:

- control mod publishes `AgesOfSiegeIntegrationApi`
- traders mod consumes it via `AgeOfSiegeAccess`

The control mod exports functions for:

- age snapshot
- treasury snapshot
- deposit coin
- credit treasury
- spend treasury
- set treasury balance
- set bank position
- clear bank position
- try track bank

The traders mod reconstructs request payloads using raw `NbtCompound`.

## Problems with the current seam

- keys are strings
- commands are anonymous function objects in an object-share map
- payload contracts are implicit instead of typed
- versioning is absent
- capability detection is weak
- failure behavior collapses to defaults rather than explicit compatibility reporting

### Refactor direction

Move to typed integration contracts:

- `AgeSnapshot`
- `TreasurySnapshot`
- `BankSnapshot`
- `TreasuryCommandPort`
- `BankTrackingPort`
- `IntegrationCapabilities`

If staying in two repos only, those types should live in an internal `core` package inside the control mod until or unless a shared third module is extracted.

## Subsystem Findings And Refactor Actions

## 1. Persistent State And Domain Ownership

### Current state

`SiegeBaseState` is carrying too much responsibility.

It currently owns or directly touches:

- settlement position and owner
- age progression
- selected siege
- rally and assault positions
- tracked bank state and bank health
- objective health
- wall health
- defender roster
- active runtime session
- pending report
- compatibility migration state

### Problems

- too many unrelated mutation paths on one object
- migration logic remains embedded in runtime state loading
- difficult to reason about ownership
- encourages broad “read everything, mutate one giant object” patterns

### Refactor actions

Split into:

- `SettlementState`
  - base position
  - owner
  - dimension
  - rally point
- `CampaignProgressState`
  - age level
  - completed sieges
  - selected siege
- `DefenseRosterState`
  - placed defenders
  - defender mutations and lookup
- `BankDefenseState`
  - tracked bank position
  - bank protection cap
  - bank health
- `SiegeRuntimeState`
  - active session
  - wall damage
  - pending report

Migration should be handled by:

- one read-time migrator from legacy NBT into new aggregates
- then runtime code only talks to the new model

## 2. Siege Runtime And AI

### Current state

The control mod has already moved toward a better structure:

- `SiegeDirector`
- `SiegePlanner`
- `BattlefieldObservationService`
- `SiegeSpawner`
- `SiegeUnitController`
- `RamController`

But the code still has transitional duplication and controller-heavy logic.

### Problems

- `SiegeDirector` is still a large orchestration and transition class
- `SiegeUnitController` still directly mixes target selection, movement, wall attack, objective attack, escorting, and fallback logic
- unit behavior depends on many embedded constants
- `SiegeManager` still exists as a legacy/parallel path
- runtime behavior is still harder to extend than it should be

### Refactor actions

Finish the runtime split:

- keep `SiegeDirector` as orchestration only
- move phase transition rules into `SiegePhaseMachine`
- move role assignment into `RoleAssignmentService`
- move target selection into `UnitTargetingService`
- move objective selection into `ObjectiveTargetService`
- move structural attack into `BreachAttackService`
- move fallback breach behavior into `FallbackPressureService`
- keep `RamController` separate

Introduce typed AI building blocks:

- `UnitCapability`
- `UnitOrder`
- `UnitTactic`
- `TargetPriorityProfile`

Benefits:

- new attacker roles can be added without editing giant switches
- age-specific behaviors can be swapped via data or strategy objects
- traders mod can later gain guards/escorts without duplicating warfare heuristics

### Data-driven tuning to extract

- aggro ranges
- escort distances
- objective attack range
- wall damage timing
- fallback replan intervals
- rush thresholds
- planner cone/search limits

## 3. Defender Runtime And Token Progression

### Current state

The defender system has solid product ideas:

- deployable tokens
- recall tooling
- persistent token data
- runtime post enforcement
- workbench progression

### Problems

- `DefenderRuntimeService` contains role behavior and peace/siege rules directly
- `DefenderTokenData` is both token schema, progression logic, naming system, loadout application, NBT migration utility, and entity stat application
- workbench progression is still tightly bound to raw NBT keys
- future archetypes would likely expand conditionals inside the same classes

### Refactor actions

Split defender logic into:

- `DefenderProfile`
  - typed profile derived from token data
- `DefenderProfileCodec`
  - NBT read/write and migration
- `DefenderLoadoutService`
  - apply items/armor/loadout from profile
- `DefenderProgressionService`
  - XP, points, stat upgrades, armor unlocks
- `DefenderRuntimePolicy`
  - interface for runtime behavior
- `ArcherPostPolicy`
- `SoldierHoldPositionPolicy`

Then make the token/item system depend on the profile model rather than manipulating nested NBT directly in many places.

### Future-ready benefits

- easier addition of defender classes beyond soldier/archer
- easier workbench expansion
- easier balance tuning by age or faction
- easier quest/event hooks tied to defender lifecycle

## 4. UI Architecture

### Current state

The UI family is visually converging but structurally split:

- `ArmyLedgerScreen` extends `Screen` and carries a huge amount of custom frame/layout/render logic
- `SiegeWarReportScreen` is another standalone `Screen`
- `ArmyWorkBenchScreen` is another standalone `Screen`
- trader handled screens already share `ScaledTradersHandledScreen`, `TradersUiLayout`, and `TradersUiTheme`

### Main issue

The product has one visual language but several independent UI bases.

### Refactor actions

Create one shared UI platform used by both repos:

- `SharedUiTheme`
  - frame colors
  - panel colors
  - typography colors
  - bar colors
- `SharedUiLayout`
  - centered frame math
  - common padding and section helpers
- `FullscreenPanelScreen`
  - base for ledger, war report, workbench, and future command screens
- `ScaledHandledScreenBase`
  - shared handled screen base for traders, bank, quest, and similar container UIs

Shared reusable widgets/helpers:

- stat rows
- progress bars
- wrapped section headers
- panel cards
- inventory tray drawing
- slot frame drawing
- button strips

### Practical migration order

1. lift color/layout helpers out of trader screen system into a shared UI package
2. create `FullscreenPanelScreen`
3. move war report and workbench first
4. then move the much larger army ledger
5. keep traders on `ScaledHandledScreenBase`, but make it use the same theme/layout tokens

### Result

- less copy-paste
- easier visual consistency
- future screens can be assembled from sections instead of redrawing a whole interface each time

## 5. Trader Economy And Offers

### Current state

Trader stock is currently hardcoded in `TraderOfferRegistry`.

That includes:

- category
- age requirements
- treasury tier
- price
- item stack resolution

### Problems

- expansion requires code edits for every new offer
- content balancing is tied to code deployments
- pack-specific item dependencies are embedded directly in Java

### Refactor actions

Move offer data into JSON definitions:

- category
- min age
- treasury tier
- price
- item id
- count
- fallback item id
- optional flags such as `rare`, `caravan_only`, `requires_mod`

Add:

- `TraderOfferDefinition`
- `TraderOfferLoader`
- `TraderOfferValidator`
- `TraderOfferResolver`

Keep code fallbacks only for development safety.

### Future-ready benefits

- easier balancing
- easier modpack-specific overrides
- easier new categories and age tiers
- easier optional dependency handling

## 6. Quest System

### Current state

The quest system already has a usable loop:

- quest offers
- active quest
- kill and collect progress
- journal sync
- guild reputation rewards

### Problems

- `QuestRegistry` is hardcoded content
- `QuestManager` owns too many behaviors
- siege-related target matching is based on IDs and tags embedded in code
- hooks run globally every tick for all players

### Refactor actions

Split into:

- `QuestDefinition`
- `QuestDefinitionLoader`
- `QuestProgressService`
- `QuestRewardService`
- `QuestTargetResolver`
- `QuestOfferGenerator`

Replace raw target matching with typed target resolvers:

- entity id resolver
- item id resolver
- siege target tag resolver
- custom predicate resolver

Optimize hooks:

- refresh collection quests on inventory-relevant changes where possible
- keep periodic refresh only as fallback
- separate temporary player buffs from quest update polling

## 7. Bank And Treasury

### Current state

The shared treasury is stored in the control mod and consumed from traders.

This is correct.

### Problems

- the treasury model is too thin for future expansion
- bank defense rules live as extra fields on siege state
- trader bank code is a thin pass-through with no explicit contract model

### Refactor actions

Introduce a richer shared economy model:

- `TreasuryState`
  - balance
  - deposited coin counts
  - optional income/loss ledger later
- `BankDefenseState`
  - tracked bank position
  - bank health
  - protection cap
  - bank status

Publish typed snapshots through the integration contract.

Benefits:

- future bank raids
- future treasury events
- better trader unlock reporting
- cleaner ownership split

## 8. From-Scratch Target Design

If the whole system were started again, the clean target would be:

### Module layout

#### `ages-of-siege-core`

Owns:

- typed contracts
- age and progression abstractions
- treasury/bank shared abstractions
- shared config/data loading
- shared UI theme/layout utilities
- shared events/capabilities

#### `ages-of-siege-warfare`

Owns:

- settlement claim
- defenders/tokens/workbench
- siege planner/controllers/spawner/rewards
- campaign/war fullscreen UIs

#### `ages-of-siege-traders`

Owns:

- traders/bank/quests/caravans
- guild progression
- trader screens and NPCs

### Best practical version from today

Without creating a third repo immediately:

- keep `Ages Of Siege Control Mod` as internal `core + warfare`
- keep `_tmp_age_of_siege_traders` as economy/trader
- create internal packages inside control mod that behave like `core`
- let traders consume only those packages through a typed boundary

## Concrete Phased Refactor Program

## Phase 1: Inventory And Mapping

Deliverables:

- subsystem inventory for both repos
- call-flow diagrams for major loops
- ownership map showing which mod owns which concepts
- dead/transitional code list

Key outputs to produce:

- claim settlement -> lock siege -> start siege -> resolve outcome
- deploy token -> runtime defender -> recall token -> workbench update
- deposit coin -> treasury unlock -> trader stock -> guild progression
- quest accept -> progress -> claim -> reputation update
- open ledger/workbench/report and trader/bank/quest screens

## Phase 2: Typed Contract Layer

Deliverables:

- typed contract package in control mod
- traders adapter rewritten to typed accessors
- capability/version reporting
- removal of raw `NbtCompound` command assembly from traders

This should happen before any large runtime or UI migration.

## Phase 3: State Decomposition

Deliverables:

- split state aggregates
- migration loader
- runtime services updated to new state ownership
- compatibility shim isolated to migration boundary only

## Phase 4: UI Foundation

Deliverables:

- shared theme/layout package
- `FullscreenPanelScreen`
- `ScaledHandledScreenBase`
- shared panel/card/stat/progress helpers

Migration order:

- war report
- workbench
- traders/bank/quest theme alignment
- army ledger last

## Phase 5: Runtime Service Split

Deliverables:

- siege phase machine
- role assignment service
- targeting service
- breach/objective services
- defender runtime policies
- externalized tuning data

## Phase 6: Content Externalization

Deliverables:

- data-driven trader offers
- data-driven quests
- data-driven guild thresholds
- data-driven defender/workbench tuning
- data-driven runtime combat/planner thresholds

## Phase 7: Cleanup And Documentation

Deliverables:

- remove legacy manager path
- remove stringly typed integration glue
- remove old compatibility fields from hot runtime state
- add architecture diagrams and developer notes
- add validation and test coverage gates

## Recommended Test Strategy

### Cross-mod tests

- traders reads age, treasury, and bank snapshots correctly
- missing control-mod API fails clearly and safely
- contract version mismatch is detectable

### Persistence tests

- old saves migrate correctly
- all new state aggregates round-trip through NBT

### Runtime tests

- siege phase transitions
- breach plan refresh behavior
- defender post enforcement
- bank vs objective targeting
- quest-relevant kill propagation

### UI tests

- all current screens still open
- shared base classes preserve behavior
- layout remains correct across resolutions

### Content validation

- invalid offer definitions are rejected
- invalid quest definitions are rejected
- invalid formation/battle data is rejected

## Top Priority Refactor Actions

If only a small number of changes can happen first, do these in order:

1. typed integration contract between mods
2. state decomposition of `SiegeBaseState`
3. shared UI foundation
4. remove hardcoded trader offers and quests
5. split runtime policies out of siege and defender controllers

These five changes give the biggest long-term payoff for clarity, extensibility, and maintainability.

## Closing Recommendation

The current project already has enough real gameplay structure that it should not be restarted.

Instead:

- keep the two mods separate
- stabilize the boundary
- pull shared concepts into a logical core layer
- externalize content
- split large runtime and UI classes into reusable pieces

That path gives the best balance of:

- low migration risk
- better maintainability
- future expansion readiness
- cleaner mental model for how both mods fit together
