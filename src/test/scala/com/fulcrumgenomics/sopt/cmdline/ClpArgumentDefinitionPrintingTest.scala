/*
 * The MIT License
 *
 * Copyright (c) 2016 Fulcrum Genomics LLC
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 *
 */

package com.fulcrumgenomics.sopt.cmdline

import java.util

import com.fulcrumgenomics.sopt.util.TermCode
import com.fulcrumgenomics.sopt.util.UnitSpec
import org.scalatest.BeforeAndAfterAll

// Sealed case class hierarchies for testing possibleValues()
sealed trait Foo
case object Hi extends Foo
case object Lo extends Foo
case object Whee extends Foo

sealed trait Bar
case object Alice extends Bar
case object Bob extends Bar
case object Eve extends Bar
object Bar {
  def values: Seq[Bar] = Seq(Alice, Eve, Bob)
}

sealed trait FooBar
case object Jack extends FooBar
case object Jill extends FooBar
case object John extends FooBar
object FooBar {
  def findValues: Seq[FooBar] = Seq(John, Jill, Jack)
}

/**
  * Tests for ClpArgumentDefinitionPrinting.
  */
class ClpArgumentDefinitionPrintingTest extends UnitSpec with BeforeAndAfterAll {

  import com.fulcrumgenomics.sopt.cmdline.ClpArgumentDefinitionPrinting.{makeDefaultValueString, ArgumentOptionalValue}

  private var printColor = true

  override protected def beforeAll(): Unit = {
    printColor = TermCode.printColor
    TermCode.printColor = false
  }

  override protected def afterAll(): Unit = {
    TermCode.printColor = printColor
  }

  "ClpArgumentDefinitionPrinting.makeDefaultValueString" should "print the default value" in {
    makeDefaultValueString(None) shouldBe s"[[$ArgumentOptionalValue]]."
    makeDefaultValueString(Some(None)) shouldBe s"[[$ArgumentOptionalValue]]."
    makeDefaultValueString(Some(Nil)) shouldBe s"[[$ArgumentOptionalValue]]."
    makeDefaultValueString(Some(Set.empty)) shouldBe s"[[$ArgumentOptionalValue]]."
    makeDefaultValueString(Some(new util.ArrayList[java.lang.Integer]())) shouldBe s"[[$ArgumentOptionalValue]]."
    makeDefaultValueString(Some(Some("Value"))) shouldBe "[[Default: Value]]."
    makeDefaultValueString(Some("Value")) shouldBe "[[Default: Value]]."
    makeDefaultValueString(Some(Some(Some("Value")))) shouldBe "[[Default: Some(Value)]]."
    makeDefaultValueString(Some(List("A", "B", "C"))) shouldBe "[[Default: A, B, C]]."
  }

  private def printArgumentUsage(name: String, shortName: Option[Char], theType: String,
                                 collectionDescription: Option[String], argumentDescription: String,
                                 optional: Boolean = false): String = {
    val stringBuilder = new StringBuilder
    ClpArgumentDefinitionPrinting.printArgumentUsage(stringBuilder=stringBuilder, name, shortName, theType, collectionDescription, argumentDescription, optional)
    stringBuilder.toString
  }

  // NB: does not test column wrapping
  "ClpArgumentDefinitionPrinting.printArgumentUsage" should "print usages" in {
    val longName    = "long-name"
    val shortName   = Option('s')
    val theType     = "TheType"
    val description = "Some description"

    printArgumentUsage(longName, shortName, theType,   None,           description).startsWith(s"-${shortName.get} $theType, --$longName=$theType") shouldBe true
    printArgumentUsage(longName, shortName, "Boolean", None,           description).startsWith(s"-${shortName.get} [[true|false]], --$longName[[=true|false]]") shouldBe true
    printArgumentUsage(longName, None     , theType,   None,           description).startsWith(s"--$longName=$theType") shouldBe true
    printArgumentUsage(longName, shortName, theType,   Some("+"),      description).startsWith(s"-${shortName.get} $theType+, --$longName=$theType+") shouldBe true
    printArgumentUsage(longName, shortName, theType,   Some("{0,20}"), description).startsWith(s"-${shortName.get} $theType{0,20}, --$longName=$theType{0,20}") shouldBe true
  }

  "ClpArgumentDefinitionPrinting.possibleValues" should "find the possible values in a sealed trait/case object hierarchy" in {
    ClpArgumentDefinitionPrinting.possibleValues(classOf[Foo]) shouldBe "Options: Hi, Lo, Whee."
  }

  it should "find the possible values in a sealed trait/case object hierarchy with a values method" in {
    ClpArgumentDefinitionPrinting.possibleValues(classOf[Bar]) shouldBe "Options: Alice, Eve, Bob."
    ClpArgumentDefinitionPrinting.possibleValues(classOf[FooBar]) shouldBe "Options: John, Jill, Jack."
  }
}
