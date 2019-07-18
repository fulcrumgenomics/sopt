/*
 * The MIT License
 *
 * Copyright (c) 2015-2016 Fulcrum Genomics LLC
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
 */

package com.fulcrumgenomics.sopt.parsing

import com.fulcrumgenomics.sopt.util.UnitSpec
import org.scalatest.PrivateMethodTester

import scala.util.Success

class OptionParserTest extends UnitSpec with PrivateMethodTester {

  "OptionParser.parse" should "fail with an OptionNameException for illegal arguments" in {
    val parser = new OptionParser // NB: no valid options!
    parser.parse("-ABC").failed.get.getClass shouldBe classOf[IllegalOptionNameException]
    parser.parse("-A-B-C").failed.get.getClass shouldBe classOf[IllegalOptionNameException]
    parser.parse("-=").failed.get.getClass shouldBe classOf[IllegalOptionNameException]
    parser.parse("-A=").failed.get.getClass shouldBe classOf[IllegalOptionNameException]
    parser.parse("-=A").failed.get.getClass shouldBe classOf[IllegalOptionNameException]
    parser.parse("-ABC=ABC").failed.get.getClass shouldBe classOf[IllegalOptionNameException]
    parser.parse("-A-B-C=ABC").failed.get.getClass shouldBe classOf[IllegalOptionNameException]
    parser.parse("--A-B-C=ABC").failed.get.getClass shouldBe classOf[IllegalOptionNameException]
  }

  it should "parse empty arguments" in {
    val parser = new OptionParser()
    parser.parse() shouldBe Success(parser)
  }

  it should "fail with an OptionNameException for an empty argument" in {
    val parser = new OptionParser()
    parser.parse("").failed.get.getClass shouldBe classOf[OptionNameException]
  }

  it should "fail with an IllegalOptionNameException for illegal options" in {
    val parser = new OptionParser
    parser.parse("-f", "foobar").failed.get.getClass shouldBe classOf[IllegalOptionNameException]
  }

  it should "parse a flag field" in {
    // short names
    new OptionParser().acceptFlag("f").get.parse("-f").get.foreach {
      case (optionName, optionValues) =>
        optionName shouldBe "f"
        optionValues shouldBe List("true")
    }
    new OptionParser().acceptFlag("f").get.parse("-f", "true").get.foreach {
      case (optionName, optionValues) =>
        optionName shouldBe "f"
        optionValues shouldBe List("true")
    }
    new OptionParser().acceptFlag("f").get.parse("-f", "false").get.foreach {
      case (optionName, optionValues) =>
        optionName shouldBe "f"
        optionValues shouldBe List("false")
    }
    new OptionParser().acceptFlag("f").get.parse("-ffalse").get.foreach {
      case (optionName, optionValues) =>
        optionName shouldBe "f"
        optionValues shouldBe List("false")
    }
    new OptionParser().acceptFlag("f").get.parse("-f=false").get.foreach {
      case (optionName, optionValues) =>
        optionName shouldBe "f"
        optionValues shouldBe List("false")
    }
    // long names
    new OptionParser().acceptFlag("flag").get.parse("--flag").get.foreach {
      case (optionName, optionValues) =>
        optionName shouldBe "flag"
        optionValues shouldBe List("true")
    }
    new OptionParser().acceptFlag("flag").get.parse("--flag", "true").get.foreach {
      case (optionName, optionValues) =>
        optionName shouldBe "flag"
        optionValues shouldBe List("true")
    }
    new OptionParser().acceptFlag("flag").get.parse("--flag", "false").get.foreach {
      case (optionName, optionValues) =>
        optionName shouldBe "flag"
        optionValues shouldBe List("false")
    }
    new OptionParser().acceptFlag("flag").get.parse("--flag=false").get.foreach {
      case (optionName, optionValues) =>
        optionName shouldBe "flag"
        optionValues shouldBe List("false")
    }
    new OptionParser().acceptFlag("this-is-a-flag").get.parse("--this-is-a-flag=false").get.foreach {
      case (optionName, optionValues) =>
        optionName shouldBe "this-is-a-flag"
        optionValues shouldBe List("false")
    }
  }

  it should "parse multiple long option names with their values separated by an equals sign, when there value is of length one" in {
    val parser = new OptionParser().acceptSingleValue("single-a").get.acceptSingleValue("single-b").get
    parser.parse("--single-b=3", "--single-a=1") shouldBe Symbol("success")
    parser should have size 2
    parser.foreach { case (observedArgument, optionValues) =>
      val found = observedArgument match {
        case "single-a" =>
          optionValues.toList should contain("1")
          true
        case "single-b" =>
          optionValues.toList should contain("3")
          true
        case _ => false
      }
      found shouldBe true
    }
  }

  it should "fail with an IllegalFlagValueException for an illegal value for a flag field" in {
    val parser = new OptionParser().acceptFlag("f").get
    parser.parse("-f", "foobar").failed.get.getClass shouldBe classOf[IllegalFlagValueException]
  }

  "OptionParser" should "should parse a basic command line" in {
    val parser = new OptionParser().acceptFlag("f").get.acceptSingleValue("s").get.acceptMultipleValues("m").get
    parser.parse("-f", "false", "-s", "single-value", "-m", "multi-value-1", "multi-value-2") shouldBe Symbol("success")
    parser should have size 3
    parser.foreach { case (observedArgument, optionValues) =>
      val found = observedArgument match {
        case "f" =>
          optionValues.toList should contain("false")
          true
        case "s" =>
          optionValues.toList should contain("single-value")
          true
        case "m" =>
          optionValues.toList should contain("multi-value-1")
          optionValues.toList should contain("multi-value-2")
          true
        case _ => false
      }
      found shouldBe true
    }
  }

  it should "should parse a very basic command line" in {
    List(true, false).foreach { strict =>
      val parser = new OptionParser().acceptFlag("f").get.acceptSingleValue("s").get.acceptMultipleValues("m").get
      parser.parse("-f", "-s", "single-value", "-m", "multi-value-1", "multi-value-2") shouldBe Symbol("success")
      parser should have size 3
      parser.foreach { case (observedArgument, optionValues) =>
        val found = observedArgument match {
          case "f" =>
            optionValues.toList should contain("true")
            true
          case "s" =>
            optionValues.toList should contain("single-value")
            true
          case "m" =>
            optionValues.toList should contain("multi-value-1")
            optionValues.toList should contain("multi-value-2")
            true
          case _ => false
        }
        found shouldBe true
      }
    }
  }

  // NB: not re-testing many of the failure conditions already tested in OptionLookup and ArgTokenizer
  it should "parse a command line with a flag argument" in {
    List(true, false).foreach { strict =>
      List(List[String]("-f"), List[String]("-f", "false"), List[String]("-f=false")).foreach { args =>
        val parser = new OptionParser().acceptFlag("f").get.acceptSingleValue("s").get.acceptMultipleValues("m").get
        parser.parse(args: _*) shouldBe Symbol("success")
        parser should have size 1
        parser.foreach { case (observedArgument, optionValues) =>
          val found = observedArgument match {
            case "f" =>
              if (0 == args.head.compareTo("-f") && args.length == 1) {
                optionValues.toList should contain("true")
              }
              else {
                optionValues.toList should contain("false")
              }
              true
            case _ => false
          }
          found shouldBe true
        }
        parser.remaining shouldBe Symbol("empty")
      }
    }
  }

  "OptionParser.remaining" should "return all arguments that were not parsed correctly" in {
    {
      val args = List("val0")
      val parser = new OptionParser().acceptSingleValue("t").get.acceptSingleValue("s").get.acceptSingleValue("u").get
      parser.parse(args: _*) shouldBe Symbol("failure")
      parser.remaining should have size 1
      parser.remaining should be(List("val0"))
    }
    {
      val args = List("-s", "val1", "val2")
      val parser = new OptionParser().acceptSingleValue("s").get
      parser.parse(args: _*) shouldBe Symbol("failure")
      parser.remaining should have size 3
      parser.remaining should be(List("-s", "val1", "val2"))
    }
    {
      val args = List("-t", "val0", "-s", "val1", "val2")
      val parser = new OptionParser().acceptSingleValue("t").get.acceptSingleValue("s").get
      parser.parse(args: _*) shouldBe Symbol("failure")
      parser.remaining should have size 3
      parser.remaining should be(List("-s", "val1", "val2"))
    }
    {
      val args = List("-t", "val0", "-s", "val1", "val2", "-u", "val3")
      val parser = new OptionParser().acceptSingleValue("t").get.acceptSingleValue("s").get.acceptSingleValue("u").get
      parser.parse(args: _*) shouldBe Symbol("failure")
      parser.remaining should have size 5
      parser.remaining should be(List("-s", "val1", "val2", "-u", "val3"))
    }
  }
}
