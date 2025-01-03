package scalangband.model.player

import scalangband.bridge.actionresult.{ActionResult, NoResult}
import scalangband.model.effect.{Effect, EffectType}

import scala.collection.mutable

class Effects(effectsByType: mutable.Map[EffectType, Effect]) {
  def addEffect(effect: Effect): ActionResult = {
    effectsByType.get(effect.effectType) match {
      case Some(existingEffect) =>
        existingEffect + effect
        effect.effectType.affectedMoreResult
      case None =>
        effectsByType.put(effect.effectType, effect)
        effect.effectType.affectedResult
    }
  }

  def hasEffect(effectType: EffectType): Boolean = {
    effectsByType.contains(effectType)
  }

  def beforeNextAction(callback: PlayerCallback): List[ActionResult] = {
    var results: List[ActionResult] = List.empty

    effectsByType.values.foreach { effect =>
      effect.turns = effect.turns - 1
      if (effect.turns <= 0) {
        effectsByType.remove(effect.effectType)
        results = effect.effectType.clearedResult :: results
      } else {
        effect.onNewTurn(callback)
      }
    }

    results
  }

  def reduceEffect(effectType: EffectType, amount: Int): ActionResult = {
    if (hasEffect(effectType)) {
      val effect = effectsByType(effectType)      
      effect.turns = effect.turns - amount
      if (effect.turns <= 0) {
        effectsByType.remove(effectType)
        effectType.clearedResult
      } else {
        effectType.affectedLessResult
      }
    } else {
      NoResult
    }
  }
  
  override def toString: String = effectsByType.values.mkString("[", ",", "]")
}
object Effects {
  def none(): Effects = new Effects(mutable.Map.empty)
}
