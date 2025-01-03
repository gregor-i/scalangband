package scalangband.data.monster.reptile

import scalangband.bridge.rendering.TextColors.LightUmber
import scalangband.model.monster.action.{HearingBoundedAction, MeleeAttacks, MonsterActions, PathfindingAction, RandomMovementAction}
import scalangband.model.monster.attack.BiteAttack
import scalangband.model.monster.{MonsterFactory, MonsterSpec, Reptile}
import scalangband.model.util.{DiceRoll, Weighted}

object RockLizard extends MonsterFactory {
  override val spec: MonsterSpec = MonsterSpec(
    name = "Rock Lizard",
    archetype = Reptile,
    depth = 1,
    health = DiceRoll("4d3"),
    hearing = 20,
    armorClass = 4,
    sleepiness = 15,
    experience = 2,
    actions = actions,
    color = LightUmber
  )

  private def actions = MonsterActions(
    adjacent = Seq(Weighted(100, MeleeAttacks(new BiteAttack(DiceRoll("1d1"))))),
    los = Seq(Weighted(100, PathfindingAction)),
    otherwise = Seq(Weighted(100, HearingBoundedAction(PathfindingAction, RandomMovementAction)))
  )
}
