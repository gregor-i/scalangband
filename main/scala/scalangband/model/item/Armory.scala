package scalangband.model.item

import scalangband.data.item.armor.body.*
import scalangband.data.item.lightsource.*
import scalangband.data.item.money.*
import scalangband.data.item.weapon.*
import scalangband.model.level.Levels
import scalangband.model.util.Weighted

import scala.util.Random

class Armory(factories: Seq[ItemFactory]) {
  private val factoriesByLevel: Map[Int, Seq[Weighted[ItemFactory]]] = {
    (1 to 100).map(level => (level, factories.filter(factory => factory.levels.contains(level))))
      .map((level, factories) => (level, factories.map(factory => Weighted(factory.commonness, factory))))
      .toMap
  }

  def generateItem(random: Random, depth: Int): Item = {
    val level = Levels(depth).items.randomInRange(random)
    val factory = Weighted.selectFrom(random, factoriesByLevel(level))

    val quality = Weighted.selectFrom(random, Levels(depth).quality)

    factory(random, quality)
  }

  def generateItem(random: Random, archetype: ItemArchetype, depth: Int): Item = {
    val level = Levels(depth).items.randomInRange(random)
    val factories = factoriesByLevel(level).filter(w => w.item.archetype == archetype)
    val factory = Weighted.selectFrom(random, factories)

    val quality = Weighted.selectFrom(random, Levels(depth).quality)

    factory(random, quality)
  }
}
object Armory {
  def apply(): Armory = new Armory(Seq(
    // Money
    CopperCoins,

    // Light Sources
    WoodenTorch,
    Lantern,

    // Weapons
    Dagger,
    MainGauche,

    // Soft Body Armors
    Robe,
    SoftLeatherArmor,
  ))
}