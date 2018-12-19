package models

import org.scalatest.{FlatSpec, Matchers}
import org.mockito.Mockito._

class FilterSpec extends FlatSpec with Matchers {
  implicit val meta = mock(classOf[NodeMeta])
  implicit val references = new References(Map.empty)
  def mockFunction[T,K](arg:T, returnValue:K) = PartialFunction[T,K] {
    case arg => returnValue
  }


  "And" should "be true only in case both filters are true" in {
    val trueFilter = mock(classOf[Filter[Any]])
    val falseFilter = mock(classOf[Filter[Any]])
    val node = mock(classOf[Node])

    when(trueFilter.matches(node)).thenReturn(true)
    when(falseFilter.matches(node)).thenReturn(false)

    And(trueFilter, trueFilter).matches(node) should be(true)
    And(trueFilter, falseFilter).matches(node) should be(false)
    And(falseFilter, trueFilter).matches(node) should be(false)
    And(falseFilter, falseFilter).matches(node) should be(false)
  }
  "Or" should "be false only in case both filters are false" in {
    val trueFilter = mock(classOf[Filter[Any]])
    val falseFilter = mock(classOf[Filter[Any]])
    val node = mock(classOf[Node])

    when(trueFilter.matches(node)).thenReturn(true)
    when(falseFilter.matches(node)).thenReturn(false)

    Or(trueFilter, trueFilter).matches(node) should be(true)
    Or(trueFilter, falseFilter).matches(node) should be(true)
    Or(falseFilter, trueFilter).matches(node) should be(true)
    Or(falseFilter, falseFilter).matches(node) should be(false)
  }
  "Not" should "invert the filter" in {
    val trueFilter = mock(classOf[Filter[Any]])
    val falseFilter = mock(classOf[Filter[Any]])
    val node = mock(classOf[Node])

    when(trueFilter.matches(node)).thenReturn(true)
    when(falseFilter.matches(node)).thenReturn(false)

    Not(trueFilter).matches(node) should be(false)
    Not(falseFilter).matches(node) should be(true)
  }
  "Equals" should "be true in case the path's value is equal to the value provided" in {
    val pathForValue = mock(classOf[Path])
    val node: Node = mock(classOf[Node])
    val nodeNotHavingValue: Node = mock(classOf[Node])

    when(node.traverse(pathForValue)).thenReturn(Some(Value("value")))
    when(nodeNotHavingValue.traverse(pathForValue)).thenReturn(None)

    Equals(pathForValue,"value").matches(node) should be(true)
    Equals(pathForValue,"value2").matches(node) should be(false)
    Equals(pathForValue,"value").matches(nodeNotHavingValue) should be(false)
  }

  "Contains" should "be true in case the path's value contains the value provided" in {
    val pathForValue = mock(classOf[Path])
    val node: Node = mock(classOf[Node])
    val nodeNotHavingValue: Node = mock(classOf[Node])

    when(node.traverse(pathForValue)).thenReturn(Some(ListValue(List(Value("value"), Value("value1")))))
    when(nodeNotHavingValue.traverse(pathForValue)).thenReturn(None)

    Contains(pathForValue,"value").matches(node) should be(true)
    Contains(pathForValue,"value2").matches(node) should be(false)
    Contains(pathForValue,"value").matches(nodeNotHavingValue) should be(false)
  }

  "Exists" should "be true in case the path contains some value" in {
    val pathForValue = mock(classOf[Path])
    val node: Node = mock(classOf[Node])
    val nodeNotHavingValue: Node = mock(classOf[Node])

    when(node.exists(pathForValue)).thenReturn(true)
    when(nodeNotHavingValue.exists(pathForValue)).thenReturn(false)

    Exists(pathForValue).matches(node) should be(true)
    Exists(pathForValue).matches(nodeNotHavingValue) should be(false)
  }

  "Refers" should "be true in case the path contains that reference" in {
    val pathForValue = mock(classOf[Path])
    val node: Node = mock(classOf[Node])
    val nodeNotHavingValue: Node = mock(classOf[Node])
    val nodeHavingNoValue: Node = mock(classOf[Node])
    val refKey = 10

    when(node.traverse(pathForValue)).thenReturn(Some(new Reference(refKey)))
    when(nodeNotHavingValue.traverse(pathForValue)).thenReturn(Some(new Reference(12)))
    when(nodeHavingNoValue.traverse(pathForValue)).thenReturn(None)

    Refers(pathForValue, refKey).matches(node) should be (true)
    Refers(pathForValue, refKey).matches(nodeNotHavingValue) should be (false)
    Refers(pathForValue, refKey).matches(nodeHavingNoValue) should be (false)
  }
}
