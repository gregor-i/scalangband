package scalangband.data.monster.bird

import scalangband.bridge.rendering.TextColors
import scalangband.model.monster.action.{HearingBoundedAction, MeleeAttacks, MonsterActions, PathfindingAction, RandomMovementAction}
import scalangband.model.monster.attack.BiteAttack
import scalangband.model.monster.{Bird, MonsterFactory, MonsterSpec}
import scalangband.model.util.{DiceRoll, Weighted}

object Crow extends MonsterFactory {
  override val spec: MonsterSpec = MonsterSpec(
    name = "Crow",
    archetype = Bird,
    depth = 2,
    health = DiceRoll("3d5"),
    hearing = 40,
    armorClass = 14,
    sleepiness = 0,
    experience = 8,
    actions = actions,
    color = TextColors.DarkGrey
  )

  private def actions = MonsterActions(
    adjacent = Seq(
      Weighted(100, MeleeAttacks(Seq(BiteAttack(DiceRoll("1d3")), BiteAttack(DiceRoll("1d3")))))
    ),
    los = Seq(Weighted(100, PathfindingAction)),
    otherwise = Seq(Weighted(100, HearingBoundedAction(PathfindingAction, RandomMovementAction)))
  )
}
