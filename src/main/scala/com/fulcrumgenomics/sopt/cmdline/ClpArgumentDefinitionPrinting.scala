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

package com.fulcrumgenomics.sopt.cmdline

import java.util

import com.fulcrumgenomics.commons.reflect.ReflectionUtil
import com.fulcrumgenomics.commons.util.StringUtil
import com.fulcrumgenomics.sopt.util.{KCYN, KGRN, MarkDownProcessor}
import com.fulcrumgenomics.commons.CommonsDef._
import com.fulcrumgenomics.sopt.Sopt

import scala.util.{Failure, Success}

object ClpArgumentDefinitionPrinting {
  /** Strings for printing enum options */
  private[cmdline] val EnumOptionDocPrefix: String = "Options: "
  private[cmdline] val EnumOptionDocSuffix: String = "."

  /**  For formatting argument section of usage message. */
  private val ArgumentColumnWidth: Int = 30
  private val DescriptionColumnWidth: Int = Sopt.TerminalWidth - ArgumentColumnWidth

  /** Markdown processor for formatting argument descriptions. */
  private val markDownProcessor = new MarkDownProcessor(lineLength=DescriptionColumnWidth)

  /** Prints the usage for a given argument definition */
  private[cmdline] def printArgumentDefinitionUsage(stringBuilder: StringBuilder,
                                                    argumentDefinition: ClpArgument,
                                                    argumentLookup: ClpArgumentLookup): Unit = {
    printArgumentUsage(stringBuilder,
      argumentDefinition.longName,
      argumentDefinition.shortName,
      argumentDefinition.typeDescription,
      makeCollectionArity(argumentDefinition),
      makeArgumentDescription(argumentDefinition, argumentLookup))
  }

  def mutexErrorHeader: String = " Cannot be used in conjunction with argument(s): "

  /**
    * Makes the full description string for the argument (that goes into the description column
    * in the argument usage) and contains the doc from the [[dagr.sopt.arg]] annotation along
    * with the default value(s), list of mutually exclusive options, and in the case of enums,
    * possible values.
    */
  private def makeArgumentDescription(argumentDefinition: ClpArgument,
                                      argumentLookup: ClpArgumentLookup): String = {
    // a secondary map where the keys are the field names
    val sb: StringBuilder = new StringBuilder
    if (argumentDefinition.doc.nonEmpty) sb.append(argumentDefinition.doc).append("  ")
    if (argumentDefinition.optional) sb.append(makeDefaultValueString(argumentDefinition.defaultValue))
    sb.append(possibleValues(argumentDefinition.unitType))

    if (argumentDefinition.mutuallyExclusive.nonEmpty) {
      sb.append(mutexErrorHeader)
      sb.append(argumentDefinition.mutuallyExclusive.map { targetFieldName =>
        argumentLookup.forField(targetFieldName) match {
          case None =>
            throw new UserException(s"Invalid argument definition in source code (see mutex). " +
              s"$targetFieldName doesn't match any known argument.")
          case Some(mutex) =>
            mutex.name + (if (mutex.shortName.nonEmpty) s" (${mutex.shortName})" else "")
        }
      }.mkString(", "))
    }
    sb.toString
  }

  private def makeCollectionArity(argumentDefinition: ClpArgument): Option[String] = {
    if (!argumentDefinition.isCollection) return None

    val desciption = (argumentDefinition.minElements, argumentDefinition.maxElements) match {
      case (0, Integer.MAX_VALUE) => "*"
      case (1, Integer.MAX_VALUE) => "+"
      case (m, n)                 => s"{${m}..${n}}"
    }
    Some(desciption)
  }

  /**
    * Intelligently decides whether or not to print a default value. Values are not printed if
    *  a) There is no default
    *  b) There is a default, but it is 'None'
    *  c) There is a default, but it's an empty list
    *  d) There is a default, but it's an empty set
    */
  private[sopt] def makeDefaultValueString(value : Option[_]) : String = {
    val vs = defaultValuesAsSeq(value)
    if (vs.isEmpty) "" else s"[Default: ${vs.mkString(", ")}]."
  }

  /** Returns the set of default values as a Seq of Strings, one per default value. */
  private [sopt] def defaultValuesAsSeq(value: Option[_]): Seq[String] = value match {
      case None | Some(None) | Some(Nil)  => Seq.empty
      case Some(s) if Set.empty == s      => Seq.empty
      case Some(c) if c.isInstanceOf[util.Collection[_]] => c.asInstanceOf[util.Collection[_]].iterator.map(_.toString).toSeq
      case Some(t) if t.isInstanceOf[Traversable[_]]     => t.asInstanceOf[Traversable[_]].toSeq.map(_.toString)
      case Some(Some(x)) => Seq(x.toString)
      case Some(x)       => Seq(x.toString)
    }


  /** Prints the usage for a given argument given its various elements */
  private[cmdline] def printArgumentUsage(stringBuilder: StringBuilder, name: String, shortName: Option[Char], theType: String,
                                          collectionArityString: Option[String], argumentDescription: String): Unit = {
    // Desired output: "-f Foo, --foo=Foo" and for Booleans, "-f [true|false] --foo=[true|false]"
    val collectionDesc = collectionArityString.getOrElse("")
    val (shortType, longType) = if (theType == "Boolean") ("[true|false]","[=true|false]") else (theType, "=" + theType)
    val label = new StringBuilder()
    shortName.foreach(n => label.append("-" + n + " " + shortType + collectionDesc + ", "))
    label.append("--" + name + longType + collectionDesc)
    stringBuilder.append(KGRN(label.toString()))

    // If the label is short enough, just pad out the column, otherwise wrap to the next line for the description
    val numSpaces: Int =  if (label.length > ArgumentColumnWidth) {
      stringBuilder.append("\n")
      ArgumentColumnWidth
    }
    else {
      ArgumentColumnWidth - label.length
    }
    stringBuilder.append(" " * numSpaces)

    val wrappedDescriptionBuilder = new StringBuilder()
    val md      = this.markDownProcessor.parse(argumentDescription)
    val padding = " " * ArgumentColumnWidth
    val lines   = this.markDownProcessor.toText(md)
    wrappedDescriptionBuilder.append(lines.head).append('\n')
    lines.tail.foreach(line => wrappedDescriptionBuilder.append(padding).append(line).append('\n'))

    stringBuilder.append(KCYN(wrappedDescriptionBuilder.toString()))
  }

  /**
    * Returns the help string with details about valid options for the given argument class.
    *
    * <p>
    * Currently this only make sense with [[Boolean]] and [[Enumeration]]. Any other class
    * will result in an empty string.
    * </p>
    *
    * @param clazz the target argument's class.
    * @return never { @code null}.
    */
  private def possibleValues(clazz: Class[_]): String = {
    if (clazz.isEnum) {
      val enumClass: Class[_ <: Enum[_ <: Enum[_]]] = clazz.asInstanceOf[Class[_ <: Enum[_ <: Enum[_]]]]
      val enumConstants = ReflectionUtil.enumOptions(enumClass) match {
        case Success(constants) => constants
        case Failure(thr) => throw thr
      }
      enumConstants.map(_.name).mkString(EnumOptionDocPrefix, ", ", EnumOptionDocSuffix)
    }
    else ""
  }
}
