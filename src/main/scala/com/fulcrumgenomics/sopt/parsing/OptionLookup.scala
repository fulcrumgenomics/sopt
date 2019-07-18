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

import com.fulcrumgenomics.sopt.util.ParsingUtil

import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.util.{Failure, Success, Try}

// NB: no manipulation of the values or names should be performed prior to calling addOptionValues...

/** Helper methods for looking up options, including their names, types, and values */
protected object OptionLookup {
  /** The type of argument we should parse */
  object OptionType extends Enumeration {
    type OptionType = Value
    val Flag, SingleValue, MultiValue = Value
  }

  import OptionType._

  /** Stores the values from the command line for the given known argument along with its various names (aliases). */
  class OptionAndValues(val optionType: OptionType, val optionNames: Seq[String]) {

    private val values: ListBuffer[String] = new ListBuffer[String]()

    private def ensureSingleValue(optionName: String): Try[this.type] = {
      if (1 < values.size) Failure(OptionSpecifiedMultipleTimesException(s"'$optionName' specified more than once."))
      else Success(this)
    }

    private def addFlag(optionName: String, addedValues: String*): Try[this.type] = {
      addedValues.toList match {
        case Nil =>
          values += "true"
          ensureSingleValue(optionName)
        case xs :: Nil =>
          val converted = addedValues.map(convertFlagValue)
          converted.find(_.isFailure) match {
            case Some(Failure(ex)) => Failure(ex)
            case _ =>
              values ++= converted.map(_.get)
              ensureSingleValue(optionName)
          }
        case _ => Failure(TooManyValuesException(s"Trying to add more than one value for the flag option: '$optionName'"))
      }
    }

    private def addSingleValue(optionName: String, addedValues: String*): Try[this.type] = {
      addedValues.toList match {
        case Nil => Failure(TooFewValuesException(s"No values given for the single-value option: '$optionName'"))
        case xs :: Nil =>
          values ++= addedValues
          if (1 < values.size) Failure(OptionSpecifiedMultipleTimesException(s"'$optionName' specified more than once."))
          else Success(this)
        case _ => Failure(TooManyValuesException(s"Trying to add more than one value for the single-value option: '$optionName'"))
      }
    }

    def add(optionName: String, addedValues: String*): Try[this.type] = {
      if (!optionNames.exists( name => name.startsWith(optionName))) {
         Failure(IllegalOptionNameException(s"Option name '$optionName' was not found in the list of option names (or as a prefix)"))
      }
      else {
        // 1. Check that we have the correct # of addedValues based on the option type
        // 2. Add the values
        // 3. Check that we have the correct # of values based on the option type
        this.optionType match {
          case Flag =>
            addFlag(optionName = optionName, addedValues: _*)
          case SingleValue =>
            addSingleValue(optionName = optionName, addedValues: _*)
          case MultiValue =>
            if (addedValues.isEmpty) Failure(TooFewValuesException(s"No values given for the multi-value option: '$optionName'"))
            else {
              values ++= addedValues
              Success(this)
            }
        }
      }
    }

    def isEmpty: Boolean = values.isEmpty

    def nonEmpty: Boolean = values.nonEmpty

    def toList: List[String] = values.toList
  }

  /** Tries to convert the string `value` to a true or false string value.  The value must be one of
    * T|True|F|False|Y|Yes|N|No ignoring case */
  def convertFlagValue(value: String): Try[String] = {
    value.toLowerCase match {
      case v if Set[String]("true", "t", "yes", "y").contains(v) => Success("true")
      case v if Set[String]("false", "f", "no", "n").contains(v) => Success("false")
      case v => Failure(IllegalFlagValueException(s"$value does not match one of T|True|F|False|Yes|Y|No|N"))
    }
  }
}

/** Stores information about the option specifications and their associated values */
trait OptionLookup {

  import OptionLookup.OptionType._
  import OptionLookup._

  /** Map from option names and aliases to the structure that holds their values */
  protected[sopt] val optionMap: mutable.Map[String, OptionAndValues] = new mutable.HashMap[String, OptionAndValues]

  /** List of all option names. */
  protected[sopt] def optionNames = optionMap.keys

  /** Add a flag argument with the given name(s) */
  def acceptFlag(optionName: String*): Try[this.type] = {
    accept(Flag, optionName: _*)
  }

  /** Add a single value argument with the given name(s) */
  def acceptSingleValue(optionName: String*): Try[this.type] = {
    accept(SingleValue, optionName: _*)
  }

  /** Add a multi value argument with the given name(s) */
  def acceptMultipleValues(optionName: String*): Try[this.type] = {
    accept(MultiValue, optionName: _*)
  }

  /** Adds the given argument type with argument name(s) */
  private def accept(optionType: OptionType.Value, optionName: String*): Try[this.type] = {
    val optionValues = new OptionAndValues(optionType, optionName.toSeq)
    optionName.view.map { name =>
      optionMap.put(name, optionValues) match {
        case Some(_) => Failure(DuplicateOptionNameException(s"option name '$name' specified more than once"))
        case _ => Success(this)
      }
    }.find(_.isFailure) match {
      case Some(Failure(ex)) => Failure(ex)
      case _ => Success(this)
    }
  }

  /** Gets all the option names with the given string as a prefix */
  private def optionNamesWithPrefix(prefix: String): Iterable[String] = {
    this.optionNames.filter { name =>
      name.startsWith(prefix)
    }
  }

  /** Gets all the option  and values with the given string as a prefix of name */
  private def optionAndValuesWithPrefix(prefix: String): Iterable[OptionAndValues] = {
    this.optionMap
      .filter { case (name, optionAndValues) => name.startsWith(prefix) }
      .map { case (name, optionAndValues) => optionAndValues }
  }

  /** Gets the single option with this name.  If no option with the name is found, returns all options that have this
    * name as a prefix.
    */
  private[sopt] def findExactOrPrefix(optionName: String): List[OptionAndValues] = {
    // first see if the name is just in the map
    optionMap.get(optionName) match {
      case Some(v) => List(v)
      case None => // next, check abbreviations
        optionAndValuesWithPrefix(optionName).toList
    }
  }

  /** True if there is one and only one option with this name or a prefix, false otherwise. */
  def hasOptionName(optionName: String): Boolean = this.findExactOrPrefix(optionName).size == 1

  /** True if the option name or its abbreviation will return at least one value if `getOptionValues` were called, false otherwise. */
  def hasOptionValues(optionName: String): Boolean = {
    if (hasOptionName(optionName)) {
      optionValues(optionName) match {
        case Success(list) => list.nonEmpty
        case Failure(_) => false
      }
    }
    else {
      false
    }
  }

  /** Gets the single value for the option with the given name or prefix.  A success requires one and only one value. */
  def singleValue(optionName: String): Try[String] = {
    optionValues(optionName) match {
      case Success(list) if list.size == 1 => Success(list.head)
      case Success(list) if list.isEmpty   => Failure(IllegalOptionNameException(s"No values found for option '$optionName'"))
      case Success(list) if list.size > 1  => Failure(IllegalOptionNameException(s"Multiple values found for option '$optionName': " + list.mkString(", ")))
      case Failure(throwable) => Failure(throwable)
    }
  }

  /** Gets the values for the option with the given name or prefix.  A success requires at least one value. */
  def optionValues(optionName: String): Try[List[String]] = {
    this.findExactOrPrefix(optionName) match {
      case Nil =>
        Failure(IllegalOptionNameException(printUnknown(optionName)))
      case option :: Nil => Success(option.toList)
      case _ =>
        Failure(DuplicateOptionNameException(printMultipleValuesFound(optionName)))
    }
  }

  /** Adds value(s) to the given option and returns all values for the given option */
  protected[sopt] def addOptionValues(optionName: String, values: String*): Try[Iterable[String]] = {
    this.findExactOrPrefix(optionName) match {
      case Nil => Failure(IllegalOptionNameException(printUnknown(optionName)))
      case option :: Nil =>
        option.add(optionName, values:_*) match {
          case Success(opt) => Success(opt.toList)
          case Failure(failure) => Failure(failure)
        }
      case _ =>
        Failure(DuplicateOptionNameException(printMultipleValuesFound(optionName)))
    }
  }

  private[sopt] def printUnknown(optionName: String): String = {
    s"No option found with name '$optionName'.${ParsingUtil.printUnknown(optionName, optionNames)}"
  }

  private [sopt] def printMultipleValuesFound(optionName: String): String = {
    s"Multiple options found for name '$optionName': " + optionNamesWithPrefix(optionName).mkString(", ")
  }
}
