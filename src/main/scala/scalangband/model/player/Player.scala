package scalangband.model.player

import org.slf4j.LoggerFactory
import scalangband.bridge.actionresult.{ActionResult, DeathResult, MessageResult}
import scalangband.model.Game.BaseEnergyUnit
import scalangband.model.effect.{Effect, EffectType, Fear, Paralysis}
import scalangband.model.element.Element
import scalangband.model.item.armor.{Armor, BodyArmor}
import scalangband.model.item.food.Food
import scalangband.model.item.lightsource.LightSource
import scalangband.model.item.potion.Potion
import scalangband.model.item.weapon.Weapon
import scalangband.model.item.{EquippableItem, Item}
import scalangband.model.location.Coordinates
import scalangband.model.monster.Monster
import scalangband.model.player.playerclass.PlayerClass
import scalangband.model.player.race.Race
import scalangband.model.util.DiceRoll
import scalangband.model.{Creature, Game, GameCallback}

import scala.util.Random

class Player(
    name: String,
    val race: Race,
    val cls: PlayerClass,
    var baseStats: Stats,
    var health: Int,
    var satiety: Int,
    var experience: Experience,
    var levels: List[PlayerLevel],
    var money: Int,
    val inventory: Inventory,
    val equipment: Equipment,
    val effects: Effects,
    energy: Int,
    coords: Coordinates
) extends Creature(name, coords, energy) {

  val accessor: PlayerAccessor = new PlayerAccessor(this)
  val callback: PlayerCallback = new PlayerCallback(this)

  def stats: Stats = baseStats + (race.statBonus + cls.statBonus + equipment.statBonus)
  def level: Int = levels.size

  def maxHealth: Int = levels.map(_.hitpoints).sum + (stats.toHp * level).toInt
  def healthPercent: Int = (health * 100) / maxHealth
  def isDead: Boolean = health < 0

  def toHit: Int = cls.meleeSkill(level) + 3 * (equipment.toHit + stats.toHit)
  def toDamage: Int = equipment.toDamage + stats.toDamage
  def armorClass: Int = equipment.totalArmor + stats.toArmor

  def speed: Int = BaseEnergyUnit

  def lightRadius: Int = equipment.light.map(_.radius).getOrElse(0)

  private def savingThrow: Int = cls.savingThrow(level) + 3 * equipment.allEquipment.map(_.toHit).sum

  def canSeeInvisible = false

  def beforeNextAction(): List[ActionResult] = {
    var results: List[ActionResult] = List.empty

    results = effects.beforeNextAction(callback) ::: results
    if (isDead) {
      results = DeathResult() :: results
    }

    results = reduceSatiety() ::: results

    results
  }

  override def onNextTurn(): Unit = {
    regenerateEnergy()
    equipment.allEquipment.foreach(item => item.onNextTurn())
  }

  def pickUp(item: Item): ActionResult = {
    inventory.addItem(item)
    MessageResult(s"You pick up the ${item.displayName}.")
  }

  def drop(item: Item, callback: GameCallback): List[ActionResult] = {
    inventory.removeItem(item)
    val dropItemResult = callback.level.addItemToTile(coordinates, item)
    List(dropItemResult, MessageResult(s"You drop ${item.article}${item.displayName}."))
  }

  def equip(item: EquippableItem): List[ActionResult] = {
    var results: List[ActionResult] = List.empty

    inventory.removeItem(item)

    def equip(equip: Equipment => Option[EquippableItem], are: String, were: String): Unit = {
      results = MessageResult(s"$are ${item.article}${item.displayName}") :: results
      equip(equipment) match {
        case Some(item) =>
          inventory.addItem(item)
          results = MessageResult(s"$were ${item.article}${item.displayName}") :: results
        case None =>
      }
    }

    item match {
      case w: Weapon      => equip(e => e.wield(w), "You are wielding", "You were wielding")
      case l: LightSource => equip(e => e.wield(l), "Your light source is", "You were holding")
      case b: BodyArmor   => equip(e => e.wear(b), "You are wearing", "You were wearing")
    }

    results
  }

  def takeOff(takeoff: Equipment => Option[Item]): List[ActionResult] = {
    takeoff(equipment) match {
      case Some(item) =>
        inventory.addItem(item)
        item match {
          case w: Weapon      => List(MessageResult(s"You were wielding ${item.article}${item.displayName}."))
          case l: LightSource => List(MessageResult(s"You were holding ${item.article}${item.displayName}."))
          case a: Armor       => List(MessageResult(s"You were wearing ${item.article}${item.displayName}."))
        }
      case None => List.empty
    }
  }

  private def addExperience(xp: Int): List[ActionResult] = {
    experience = experience + xp / level
    val newLevel = ExperienceTable.getLevel(race, experience.current)
    if (newLevel > level) {
      var results: List[ActionResult] = List.empty
      (level + 1 to newLevel).foreach { i =>
        val newLevelActual: PlayerLevel = PlayerLevel.next(race, cls)
        levels = newLevelActual :: levels
        results = MessageResult(s"Welcome to level $i.") :: results
      }

      results
    } else {
      List.empty
    }
  }

  def attack(monster: Monster, callback: GameCallback): List[ActionResult] = {
    if (hasEffect(Fear)) {
      List(MessageResult(s"You are too afraid to attack the ${monster.displayName}!"))
    } else {
      Random.nextInt(20) + 1 match {
        case 1 => handleMiss(monster)
        case 20 => handleHit(monster, callback)
        case _ =>
          if (Random.nextInt(toHit) > monster.armorClass * 3 / 4) {
            handleHit(monster, callback)
          } else {
            handleMiss(monster)
          }
      }
    }
  }

  private def handleHit(monster: Monster, callback: GameCallback): List[ActionResult] = {
    var results: List[ActionResult] = List.empty

    val damageDice = equipment.weapon match {
      case Some(weapon) => weapon.damage
      case None         => DiceRoll("1d1")
    }
    val damage = Math.max(damageDice.roll() + toDamage, 1)
    monster.health = monster.health - damage
    monster.awake = true

    results = MessageResult(s"You hit the ${monster.displayName}.") :: results
    Player.Logger.info(s"Player hit ${monster.displayName} for $damage damage")
    if (monster.health <= 0) {
      Player.Logger.info(s"Player killed ${monster.displayName}")
      results =
        MessageResult(s"You have ${if (monster.alive) "slain" else "destroyed"} the ${monster.displayName}.") :: results
      results = callback.killMonster(monster) ::: results

      results = addExperience(monster.experience) ::: results
    }

    results
  }

  private def handleMiss(monster: Monster): List[ActionResult] = {
    Player.Logger.info(s"Player killed ${monster.displayName}")
    List(MessageResult(s"You miss the ${monster.displayName}."))
  }

  def takeDamage(damage: Int, maybeElement: Option[Element], maybeEffect: Option[Effect]): List[ActionResult] = {
    Player.Logger.info(s"Player took $damage damage of element $maybeEffect and effect $maybeEffect")
    var results: List[ActionResult] = List.empty

    val actualDamage = maybeElement match {
      case Some(element) if resists(element) => damage / 2
      case _                                 => damage
    }

    maybeElement match {
      case Some(element) =>
        element.message match {
          case Some(message) => results = MessageResult(message) :: results
          case None          =>
        }
      case None =>
    }

    health = health - actualDamage
    if (isDead) {
      results = DeathResult() :: results
    } else {
      maybeEffect.foreach { eff =>
        results = tryToAddEffect(eff) :: results
      }
    }

    results
  }

  def tryToAddEffect(effect: Effect): ActionResult = {
    if (Random.nextInt(100) > savingThrow) {
      effects.addEffect(effect)
    } else {
      MessageResult("You resist.")
    }
  }
  
  def reduceEffect(effectType: EffectType, amount: Int): ActionResult = {
    effects.reduceEffect(effectType, amount)
  }
  
  def quaff(potion: Potion, fromInventory: Boolean = true): List[ActionResult] = {
    val results = potion.onQuaff(callback)
  
    if (fromInventory) {
      inventory.removeItem(potion)
    }
    
    results
  }
  
  def eat(food: Food, fromInventory: Boolean = true): List[ActionResult] = {
    var results: List[ActionResult] = List.empty

    satiety = food.satiety.whenEaten(satiety)
    results = MessageResult(food.message) :: results

    if (fromInventory) {
      inventory.removeItem(food)
    }

    results
  }

  def reduceSatiety(amount: Int = 1): List[ActionResult] = {
    satiety = if (satiety - amount < 0) 0 else satiety - amount
    List.empty
  }

  def incapacitated: Boolean = {
    hasEffect(Paralysis)
  }

  def hasEffect(effectType: EffectType): Boolean = {
    effects.hasEffect(effectType)
  }

  private def resists(element: Element): Boolean = false

  def heal(amount: Int): ActionResult = {
    if (health + amount > maxHealth) {
      health = maxHealth
    } else {
      health = health + amount
    }
    
    MessageResult("You feel better.")
  }
  
  def fullHeal(): List[ActionResult] = {
    health = maxHealth
    List(MessageResult("You feel *much* better."))
  }
}
object Player {
  private val Logger = LoggerFactory.getLogger(classOf[Player])

  def newPlayer(random: Random, name: String, race: Race, cls: PlayerClass): Player = {
    val startingStats = cls.startingStats
    val levelOne = PlayerLevel.first(race: Race, cls: PlayerClass)

    new Player(
      name = name,
      race = race,
      cls = cls,
      baseStats = startingStats,
      health = levelOne.hitpoints + (startingStats + cls.statBonus).toHp.toInt,
      satiety = 4500,
      experience = Experience.None,
      levels = List(levelOne),
      money = DiceRoll("1d50+100").roll(),
      inventory = cls.startingInventory(random),
      equipment = cls.startingEquipment(random),
      effects = Effects.none(),
      energy = Game.BaseEnergyUnit,
      coords = Coordinates.Placeholder
    )
  }

  val MaxSatiety: Int = 5000
}

class PlayerAccessor(private val player: Player) {
  def armorClass: Int = player.armorClass
  def canSeeInvisible: Boolean = player.canSeeInvisible
  def coordinates: Coordinates = player.coordinates
  def hasEffect(effectType: EffectType): Boolean = player.hasEffect(effectType)
  def lightRadius: Int = player.lightRadius
  def satiety: Int = player.satiety
}

class PlayerCallback(private val player: Player) {
  def attack(monster: Monster, callback: GameCallback): List[ActionResult] = player.attack(monster, callback)
  def resetEnergy(): Unit = player.energy = player.speed

  def takeDamage(damage: Int, element: Option[Element] = None, effect: Option[Effect] = None): List[ActionResult] = {
    player.takeDamage(damage, element, effect)
  }
  
  def tryToAddEffect(effect: Effect): ActionResult = player.tryToAddEffect(effect)
  def reduceEffect(effect: EffectType, turns: Int): ActionResult = player.reduceEffect(effect, turns)

  def addMoney(amount: Int): Unit = player.money = player.money + amount

  def pickUp(item: Item): ActionResult = player.pickUp(item)
  def dropItem(item: Item, callback: GameCallback): List[ActionResult] = player.drop(item, callback)

  def wear(item: EquippableItem): List[ActionResult] = player.equip(item)
  def takeOff(f: Equipment => Option[Item]): List[ActionResult] = player.takeOff(f)

  def eat(food: Food): List[ActionResult] = player.eat(food)
  def quaff(potion: Potion, fromInventory: Boolean): List[ActionResult] = player.quaff(potion, fromInventory)
  
  def heal(amount: Int): ActionResult = player.heal(amount)
  def fullHeal(): List[ActionResult] = player.fullHeal()

  
  def logEquipment(): Unit = PlayerCallback.Logger.info(s"Equipment: ${player.equipment}")
}
object PlayerCallback {
  private val Logger = LoggerFactory.getLogger(classOf[PlayerCallback])
}
