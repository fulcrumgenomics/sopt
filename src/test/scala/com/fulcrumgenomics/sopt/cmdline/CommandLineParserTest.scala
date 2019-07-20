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

import com.fulcrumgenomics.commons.CommonsDef._
import com.fulcrumgenomics.commons.reflect.ReflectionUtil
import com.fulcrumgenomics.commons.util.CaptureSystemStreams
import com.fulcrumgenomics.sopt.Sopt.{CommandSuccess, Failure, SubcommandSuccess}
import com.fulcrumgenomics.sopt.{Sopt, _}
import com.fulcrumgenomics.sopt.cmdline.testing.clps._
import com.fulcrumgenomics.sopt.util.{TermCode, UnitSpec}
import org.scalatest.{BeforeAndAfterAll, OptionValues}

import scala.reflect.ClassTag
import scala.reflect.runtime.universe._

// Located here since we cannot be an inner class
@clp(description = "", group = classOf[TestGroup], hidden = true)
class CommandLineProgramValidationError(@arg var aStringSomething: String = "Default") {
  throw new ValidationException("WTF")
}

class CommandLineParserTest extends UnitSpec with CaptureSystemStreams with BeforeAndAfterAll with OptionValues {

  private val prevPrintColor = TermCode.printColor
  private val prevIncludeRequiredAndOptionalSections = CommandLineProgramParserStrings.IncludeRequiredAndOptionalSections

  override protected def beforeAll(): Unit = {
    TermCode.printColor = false
    CommandLineProgramParserStrings.IncludeRequiredAndOptionalSections = true
  }
  override protected def afterAll(): Unit = {
    TermCode.printColor = prevPrintColor
    CommandLineProgramParserStrings.IncludeRequiredAndOptionalSections = prevIncludeRequiredAndOptionalSections
  }

  private def nameOf(clazz: Class[_]): String = clazz.getSimpleName

  private object TestParseSubCommand {
    val packageList: List[String] = List[String]("com.fulcrumgenomics.sopt.cmdline.testing.clps")

    def parseSubCommand[ClpClass](args: Seq[String], extraUsage: Option[String] = None)
                  (implicit classTag: ClassTag[ClpClass], typeTag: TypeTag[ClpClass]): (CommandLineParser[ClpClass], Option[ClpClass], String)
     = {
      val parser = new CommandLineParser[ClpClass]("command-line-name")
      val subcommands = Sopt.find[ClpClass](packageList, includeHidden = true)
      val result = parser.parseSubCommand(args, subcommands, extraUsage=extraUsage)

      result match {
        case CommandSuccess(command) => (parser, Some(command), "")
        case Failure(usage)          => (parser, None, usage())
        case other                   => unreachable(s"Should not have matched: $other")
      }
    }

    def checkEmptyUsage[T](parser:  CommandLineParser[T], clpOption: Option[T], output: String): Unit = {
      clpOption shouldBe Symbol("empty")
      output should include(parser.AvailableSubCommands)
      output should not include parser.unknownSubCommandErrorMessage("")
    }

    def checkEmptyClpUsage[T](parser:  CommandLineParser[T], clpOption: Option[T], output: String, clpClazz: Class[_]): Unit = {
      val name = nameOf(clpClazz)
      clpOption shouldBe Symbol("empty")
      output should include(parser.standardSubCommandUsagePreamble(Some(clpClazz)))
      output should include(s"$name ${parser.RequiredArguments}")
      output should include(s"$name ${parser.OptionalArguments}")
    }
  }

  // required so that colors are not in our usage messages
  "CommandLineParserTest" should "have color status be false" in {
    // this should be set in the build.sbt
    TermCode.printColor shouldBe false
  }

  "CommandLineParser.parseSubCommand" should "print just the available Clps and a missing Clp error when no arguments or the help flag are/is given" in {
    Seq(Seq[String](), Seq[String]("-h"), Seq[String]("--help")).foreach { args =>
      val (parser, clpOption, output) = TestParseSubCommand.parseSubCommand[TestingClp](args)
      TestParseSubCommand.checkEmptyUsage(parser, clpOption, output)
      if (args.isEmpty) {
        output should include(parser.AvailableSubCommands)
        output should include(parser.MissingSubCommand)
      }
    }
  }

  it should "print just the usage when an unknown Clp name is given" in {
    Seq(
      Seq[String]("--", "ClpFive"),
      Seq[String]("--", "ClpFive", "--flag"),
      Seq[String]("ClpFive", "--flag")
    ).foreach { args =>
      val (parser, clpOption, output) = TestParseSubCommand.parseSubCommand[TestingClp](args)
      clpOption shouldBe Symbol("empty")
      TestParseSubCommand.checkEmptyUsage(parser, clpOption, output)
    }
  }

  it should "print just the usage when the Clp name then no arguments, -h, or --help is given" in {
    val name = nameOf(classOf[CommandLineProgramThree])
    Seq(Seq[String](name), Seq[String](name, "-h"), Seq[String](name, "--help")).foreach { args =>
      val (parser, clpOption, output) = TestParseSubCommand.parseSubCommand[TestingClp](args)
      TestParseSubCommand.checkEmptyClpUsage(parser, clpOption, output, classOf[CommandLineProgramThree])
    }
  }

  it should "print just the command version when the Clp name then -v or --version is given" in {
    val name = nameOf(classOf[CommandLineProgramThree])
    Seq(Seq[String](name, "-v"), Seq[String](name, "--version")).foreach { args =>
      val (_, _, output) = TestParseSubCommand.parseSubCommand[TestingClp](args)
      val version = new CommandLineProgramParser(classOf[TestingClp]).version
      output should include(version)
    }
  }

  it should "print just the usage when unknown arguments are passed after a valid Clp name" in {
    val clpClazz = classOf[CommandLineProgramFour]
    val name = nameOf(clpClazz)
    Seq(
      Seq[String](name, "--helloWorld", "SomeProgram"),
      Seq[String](name, "---", "SomeProgram")
    ).foreach { args =>
      val (parser, clpOption, output) = TestParseSubCommand.parseSubCommand[TestingClp](args)
      clpOption shouldBe Symbol("empty")
      output should include(parser.standardSubCommandUsagePreamble(Some(clpClazz)))
      output should include(s"No option found with name '${args(1).substring(2)}'")
    }
  }

  it should "list all command names when multiple have the same prefix of the given command argument" in {
    val (_, _, output) = TestParseSubCommand.parseSubCommand[TestingClp](Seq[String](" CommandLineProgram"))
    output should include(nameOf(classOf[CommandLineProgramOne]))
    output should include(nameOf(classOf[CommandLineProgramTwo]))
    output should include(nameOf(classOf[CommandLineProgramThree]))
    output should include(nameOf(classOf[CommandLineProgramFour]))
    // ... should match them all!
  }

  it should "return a valid clp with and valid arguments" in {
    val clpClazz = classOf[CommandLineProgramThree]
    val name = nameOf(clpClazz)
    Seq(
      Seq[String](name, "--argument", "value")
    ).foreach { args =>
      val (parser, clpOption, output) = TestParseSubCommand.parseSubCommand[TestingClp](args)
      clpOption shouldBe Symbol("defined")
      clpOption.get.getClass shouldBe clpClazz
      clpOption.get.asInstanceOf[CommandLineProgramThree].argument shouldBe "value"
      output shouldBe Symbol("empty")
      parser.commandLine.value shouldBe "CommandLineProgramThree --argument value"
    }
  }

  it should "print an extra usage if desired" in {
    val args = Seq[String]("-h")
    val (_, _, output) = TestParseSubCommand.parseSubCommand[CommandLineProgramThree](args, extraUsage=Some("HELLO WORLD"))
    output should include ("HELLO WORLD")
  }

  "CommandLineParser.formatShortDescription" should "print everything before the first period when present" in {
    val parser = new CommandLineParser[CommandLineParserTest](commandLineName="Name")
    parser.formatShortDescription("A.B.C.D.") shouldBe "A."
    parser.formatShortDescription("blah. Tybalt, here slain, whom Romeo's hand did slay; blah. blah.") shouldBe "blah."
  }

  it should "print at most the maximum line length" in {
    val parser = new CommandLineParser[CommandLineParserTest](commandLineName="Name")
    parser.formatShortDescription("A B C D E F G") shouldBe "A B C D E F G"
    parser.formatShortDescription("A" * (parser.SubCommandDescriptionLineLength+1)) shouldBe (("A" * (parser.SubCommandDescriptionLineLength-3)) + "...")
  }

  private object TestParseCommandAndSub {
    val packageList: List[String] = List[String]("com.fulcrumgenomics.sopt.cmdline.testing.clps")

    def parseCommandAndSub[Command:TypeTag:ClassTag,Subcommand:TypeTag:ClassTag](args: Seq[String]):
    (CommandLineParser[Subcommand], Option[Command], Option[Subcommand], String) = {
      val commandClass: Class[Command] = ReflectionUtil.typeTagToClass[Command]
      val parser = new CommandLineParser[Subcommand](commandClass.getSimpleName)
      val subcommands = Sopt.find[Subcommand](packageList, includeHidden=true)

      parser.parseCommandAndSubCommand[Command](args, subcommands) match {
        case SubcommandSuccess(cmd, sub) => (parser, Some(cmd), Some(sub), "")
        case Failure(usage)              => (parser, None, None, usage())
        case other                       => unreachable(s"Should not have gotten $other")
      }
    }

    def checkEmptyUsage[commandClass,ClpClass](parser:  CommandLineParser[ClpClass],
                                            commandOption: Option[commandClass],
                                            clpOption: Option[ClpClass],
                                            output: String)
                                           (implicit ttCommand: TypeTag[commandClass])
    : Unit = {
      val commandClazz: Class[commandClass] = ReflectionUtil.typeTagToClass[commandClass]
      val commandName = commandClazz.getSimpleName
      commandOption shouldBe Symbol("empty")
      clpOption shouldBe Symbol("empty")
      output should include(parser.standardCommandAndSubCommandUsagePreamble(commandClazz=Some(commandClazz), subCommandClazz=None))
      output should include(s"$commandName ${parser.OptionalArguments}")
      output should include(parser.AvailableSubCommands)
    }

    def checkEmptyClpUsage[commandClass,ClpClass](parser:  CommandLineParser[ClpClass], commandOption: Option[commandClass], clpOption: Option[ClpClass], output: String)
                                              (implicit ttCommand: TypeTag[commandClass], ttClp: TypeTag[ClpClass])
    : Unit = {
      val commandClazz: Class[commandClass] = ReflectionUtil.typeTagToClass[commandClass]
      val clpClazz: Class[ClpClass] = ReflectionUtil.typeTagToClass[ClpClass]
      val commandName = nameOf(commandClazz)
      val name = nameOf(clpClazz)
      commandOption shouldBe Symbol("empty")
      clpOption shouldBe Symbol("empty")
      output should include(parser.standardCommandAndSubCommandUsagePreamble(commandClazz=Some(commandClazz), subCommandClazz=Some(clpClazz)))
      // NB: required arguments are not checked since the command class has all optional arguments
      //output should include(s"$commandName ${parser.RequiredArguments}")
      output should include(s"$commandName ${parser.OptionalArguments}")
      output should include(s"$name ${parser.RequiredArguments}")
      output should include(s"$name ${parser.OptionalArguments}")
    }
  }

  "CommandLineParser.parseCommandAndSubCommand" should "print just the command usage when no arguments or the help flag are/is given" in {
    Seq(Seq[String](), Seq[String]("-h"), Seq[String]("--help")).foreach { args =>
      val (parser, commandOption, clpOption, output) = TestParseCommandAndSub.parseCommandAndSub[CommandLineProgramTesting,CommandLineProgramOne](args)
      TestParseCommandAndSub.checkEmptyUsage[CommandLineProgramTesting,CommandLineProgramOne](parser=parser, commandOption=commandOption, clpOption=clpOption, output=output)
      if (args.isEmpty) {
        output should include(parser.MissingSubCommand)
      }
    }
  }

  it should "print just the command version when -v or --version is given" in {
    Seq(Seq[String]("-v"), Seq[String]("--version")).foreach { args =>
      val (_, _, _, output) = TestParseCommandAndSub.parseCommandAndSub[CommandLineProgramTesting,CommandLineProgramOne](args)
      val version: String = new CommandLineProgramParser(classOf[CommandLineProgramTesting]).version
      output should include(version)
    }
  }

  it should "print just the command usage when only command and clp separator \"--\" is given" in {
    val args = Seq[String]("--")
    val (parser, commandOption, clpOption, output) = TestParseCommandAndSub.parseCommandAndSub[CommandLineProgramTesting,CommandLineProgramOne](args)
    TestParseCommandAndSub.checkEmptyUsage[CommandLineProgramTesting,CommandLineProgramOne](parser=parser, commandOption=commandOption, clpOption=clpOption, output=output)
    output should include(parser.MissingSubCommand)
  }

  it should "print just the command usage when unknown arguments are passed to command" in {
    Seq(
      Seq[String]("-helloWorld", "CommandLineProgramFive"),
      Seq[String]("-", "CommandLineProgramFive"),
      Seq[String]("-CommandLineProgramFive")
    ).foreach { args =>
      val (parser, commandOption, clpOption, output) = TestParseCommandAndSub.parseCommandAndSub[CommandLineProgramTesting,CommandLineProgramOne](args)
      commandOption shouldBe Symbol("empty")
      clpOption shouldBe Symbol("empty")
      output should include(parser.standardCommandAndSubCommandUsagePreamble(commandClazz=Some(classOf[CommandLineProgramTesting]), subCommandClazz=None))
      output should not include parser.unknownSubCommandErrorMessage(args.head.substring(1))

    }
    Seq(
      Seq[String]("--helloWorld", "CommandLineProgramFive"),
      Seq[String]("---", "CommandLineProgramFive"),
      Seq[String]("--CommandLineProgramFive")
    ).foreach { args =>
      val (parser, commandOption, clpOption, output) = TestParseCommandAndSub.parseCommandAndSub[CommandLineProgramTesting,CommandLineProgramOne](args)
      commandOption shouldBe Symbol("empty")
      clpOption shouldBe Symbol("empty")
      output should include(parser.standardCommandAndSubCommandUsagePreamble(commandClazz=Some(classOf[CommandLineProgramTesting]), subCommandClazz=None))
      output should include(s"No option found with name '${args.head.substring(2)}'")
    }
  }

  it should "print just the command usage when an unknown clp name is passed to command" in {
    Seq(
      Seq[String]("--", "CommandLineProgramOn"),
      Seq[String]("--", "CommandLineProgramOn", "--flag"),
      Seq[String]("CommandLineProgramOn")
    ).foreach { args =>
      val (parser, commandOption, clpOption, output) = TestParseCommandAndSub.parseCommandAndSub[CommandLineProgramTesting,CommandLineProgramOne](args)
      commandOption shouldBe Symbol("empty")
      clpOption shouldBe Symbol("empty")
      output should include(parser.standardCommandAndSubCommandUsagePreamble(commandClazz=Some(classOf[CommandLineProgramTesting]), subCommandClazz=None))
      output should include(parser.unknownSubCommandErrorMessage("CommandLineProgramOn"))
    }
  }

  it should "print just the command usage when command's custom command line validation fails" in {
    Seq(
      Seq[String](nameOf(classOf[CommandLineProgramOne]))
    ).foreach { args =>
      val (parser, commandOption, clpOption, output) = TestParseCommandAndSub.parseCommandAndSub[CommandLineProgramValidationError,CommandLineProgramOne](args)
      commandOption shouldBe Symbol("empty")
      clpOption shouldBe Symbol("empty")
      output should include(parser.standardCommandAndSubCommandUsagePreamble(commandClazz=Some(classOf[CommandLineProgramValidationError]), subCommandClazz=None))
      output should include(classOf[ValidationException].getSimpleName)
      output should include("WTF")
    }
  }

  it should "print the command and clp usage when only the clp name is given" in {
    val args = Seq[String](nameOf(classOf[CommandLineProgramThree]))
    val (parser, commandOption, clpOption, output) = TestParseCommandAndSub.parseCommandAndSub[CommandLineProgramTesting,CommandLineProgramThree](args)
    commandOption shouldBe Symbol("empty")
    clpOption shouldBe Symbol("empty")
    TestParseCommandAndSub.checkEmptyClpUsage[CommandLineProgramTesting,CommandLineProgramThree](parser=parser, commandOption=commandOption, clpOption=clpOption, output=output)
  }

  it should "print the command and clp usage when only the clp name separator \"--\" and clp name are given" in {
    val args = Seq[String]("--", nameOf(classOf[CommandLineProgramThree]))
    val (parser, commandOption, clpOption, output) = TestParseCommandAndSub.parseCommandAndSub[CommandLineProgramTesting,CommandLineProgramThree](args)
    commandOption shouldBe Symbol("empty")
    clpOption shouldBe Symbol("empty")
    TestParseCommandAndSub.checkEmptyClpUsage[CommandLineProgramTesting,CommandLineProgramThree](parser=parser, commandOption=commandOption, clpOption=clpOption, output=output)
  }

  it should "print the command and clp usage when only the clp name and -h are given" in {
    val args = Seq[String](nameOf(classOf[CommandLineProgramThree]), "-h")
    val (parser, commandOption, clpOption, output) = TestParseCommandAndSub.parseCommandAndSub[CommandLineProgramTesting,CommandLineProgramThree](args)
    commandOption shouldBe Symbol("empty")
    clpOption shouldBe Symbol("empty")
    TestParseCommandAndSub.checkEmptyClpUsage[CommandLineProgramTesting,CommandLineProgramThree](parser=parser, commandOption=commandOption, clpOption=clpOption, output=output)
  }

  it should "print just the command version when -v or --version with a clp name" in {
    Seq("-v", "--version").foreach { arg =>
      val args = Seq[String](nameOf(classOf[CommandLineProgramThree]), arg)
      val (_, commandOption, clpOption, output) = TestParseCommandAndSub.parseCommandAndSub[CommandLineProgramTesting,CommandLineProgramThree](args)
      commandOption shouldBe Symbol("empty")
      clpOption shouldBe Symbol("empty")
      val version = new CommandLineProgramParser(classOf[CommandLineProgramTesting]).version
      output should include(version)
    }
  }

  it should "print the command and clp usage when only the clp name separator \"--\" and clp name and -h are given" in {
    val args = Seq[String]("--", nameOf(classOf[CommandLineProgramThree]), "-h")
    val (parser, commandOption, clpOption, output) = TestParseCommandAndSub.parseCommandAndSub[CommandLineProgramTesting,CommandLineProgramThree](args)
    commandOption shouldBe Symbol("empty")
    clpOption shouldBe Symbol("empty")
    TestParseCommandAndSub.checkEmptyClpUsage[CommandLineProgramTesting,CommandLineProgramThree](parser=parser, commandOption=commandOption, clpOption=clpOption, output=output)
  }

  it should "print the command and clp usage when unknown clp arguments are given " in {
    Seq(
      Seq[String]("--", nameOf(classOf[CommandLineProgramThree]), "--blarg", "4"),
      Seq[String](nameOf(classOf[CommandLineProgramThree]), "--blarg", "4"),
      Seq[String](nameOf(classOf[CommandLineProgramThree]), nameOf(classOf[CommandLineProgramTwo]))
    ).foreach { args =>
      val (parser, commandOption, clpOption, output: String) = TestParseCommandAndSub.parseCommandAndSub[CommandLineProgramTesting,CommandLineProgramThree](args)
      commandOption shouldBe Symbol("empty")
      clpOption shouldBe Symbol("empty")
      TestParseCommandAndSub.checkEmptyClpUsage[CommandLineProgramTesting,CommandLineProgramThree](parser=parser, commandOption=commandOption, clpOption=clpOption, output=output)
    }
  }

  it should "print the command and clp usage when required clp arguments are not specified" in {
    Seq(
      Seq[String]("--", nameOf(classOf[CommandLineProgramThree])),
      Seq[String](nameOf(classOf[CommandLineProgramThree]))
    ).foreach { args =>
      val (_, commandOption, clpOption, output) = TestParseCommandAndSub.parseCommandAndSub[CommandLineProgramTesting,CommandLineProgramThree](args)
      commandOption shouldBe Symbol("empty")
      clpOption shouldBe Symbol("empty")
      output should include(classOf[UserException].getSimpleName)
      output should include(CommandLineProgramParserStrings.requiredArgumentErrorMessage("argument"))
    }
  }

  it should "print the command and clp usage when clp arguments are specified but are mutually exclusive" in {
    Seq(
      Seq[String]("--", nameOf(classOf[CommandLineProgramWithMutex]), "--argument", "value", "--another", "value"),
      Seq[String](nameOf(classOf[CommandLineProgramWithMutex]), "--argument", "value", "--another", "value")
    ).foreach { args =>
      val (_, commandOption, clpOption, output) = TestParseCommandAndSub.parseCommandAndSub[CommandLineProgramTesting,CommandLineProgramWithMutex](args)
      commandOption shouldBe Symbol("empty")
      clpOption shouldBe Symbol("empty")
      output should include(classOf[UserException].getSimpleName)
      output should include(ClpArgumentDefinitionPrinting.mutexErrorHeader)
    }
  }

  it should "return a valid clp with and without using the clp name separator \"--\" and valid arguments" in {
    Seq(
      Seq[String]("--", nameOf(classOf[CommandLineProgramThree]), "--argument", "value"),
      Seq[String](nameOf(classOf[CommandLineProgramThree]), "--argument", "value")
    ).foreach { args =>
      val (parser, commandOption, clpOption, output) = TestParseCommandAndSub.parseCommandAndSub[CommandLineProgramTesting,CommandLineProgramThree](args)
      commandOption shouldBe Symbol("defined")
      commandOption.get.getClass shouldBe classOf[CommandLineProgramTesting]
      clpOption shouldBe Symbol("defined")
      clpOption.get.getClass shouldBe classOf[CommandLineProgramThree]
      clpOption.get.argument shouldBe "value"
      output shouldBe Symbol("empty")
      parser.commandLine.value shouldBe "CommandLineProgramTesting --a-string-something Default CommandLineProgramThree --argument value"
    }
  }

  it should "return a valid clp with and ithout using the clp name separator \"--\" and no arguments with a clp that requires no arguments" in {
    Seq(
      Seq[String]("--", nameOf(classOf[CommandLineProgramFour])),
      Seq[String](nameOf(classOf[CommandLineProgramFour]))
    ).foreach { args =>
      val (parser, commandOption, clpOption, output) = TestParseCommandAndSub.parseCommandAndSub[CommandLineProgramTesting,CommandLineProgramFour](args)
      commandOption shouldBe Symbol("defined")
      commandOption.get.getClass shouldBe classOf[CommandLineProgramTesting]
      clpOption shouldBe Symbol("defined")
      clpOption.get.getClass shouldBe classOf[CommandLineProgramFour]
      clpOption.get.argument shouldBe "default"
      output shouldBe Symbol("empty")
      output shouldBe Symbol("empty")
      parser.commandLine.value shouldBe "CommandLineProgramTesting --a-string-something Default CommandLineProgramFour --argument default --flag false"
    }
  }

  it should "return a valid clp when the class has no arguments" in {
    Seq(
      Seq[String]("--", nameOf(classOf[CommandLineProgramNoArgs])),
      Seq[String](nameOf(classOf[CommandLineProgramNoArgs]))
    ).foreach { args =>
      val (parser, commandOption, clpOption, output) = TestParseCommandAndSub.parseCommandAndSub[CommandLineProgramTesting,CommandLineProgramNoArgs](args)
      commandOption shouldBe Symbol("defined")
      commandOption.get.getClass shouldBe classOf[CommandLineProgramTesting]
      clpOption shouldBe Symbol("defined")
      clpOption.get.getClass shouldBe classOf[CommandLineProgramNoArgs]
      output shouldBe Symbol("empty")
      output shouldBe Symbol("empty")
      parser.commandLine.value shouldBe "CommandLineProgramTesting --a-string-something Default CommandLineProgramNoArgs"
    }
  }

  it should "return a valid clp that has an option with a default value" in {
    val args = Seq[String](nameOf(classOf[CommandLineProgramWithOptionSomeDefault]))
    val (parser, commandOption, clpOption, output) = TestParseCommandAndSub.parseCommandAndSub[CommandLineProgramTesting,CommandLineProgramWithOptionSomeDefault](args)
    commandOption shouldBe Symbol("defined")
    commandOption.get.getClass shouldBe classOf[CommandLineProgramTesting]
    clpOption shouldBe Symbol("defined")
    clpOption.get.getClass shouldBe classOf[CommandLineProgramWithOptionSomeDefault]
    clpOption.get.argument shouldBe Symbol("defined")
    clpOption.get.argument.get shouldBe "default"
    output shouldBe Symbol("empty")
    output shouldBe Symbol("empty")
    parser.commandLine.value shouldBe "CommandLineProgramTesting --a-string-something Default CommandLineProgramWithOptionSomeDefault --argument default"
  }

  it should "return a valid clp when an option with a default value is set to none" in {
    val args = Seq[String](nameOf(classOf[CommandLineProgramWithOptionSomeDefault]), "--argument", ReflectionUtil.SpecialEmptyOrNoneToken)
    val (parser, commandOption, clpOption, output) = TestParseCommandAndSub.parseCommandAndSub[CommandLineProgramTesting,CommandLineProgramWithOptionSomeDefault](args)
    commandOption shouldBe Symbol("defined")
    commandOption.get.getClass shouldBe classOf[CommandLineProgramTesting]
    clpOption shouldBe Symbol("defined")
    clpOption.get.getClass shouldBe classOf[CommandLineProgramWithOptionSomeDefault]
    clpOption.get.argument shouldBe Symbol("empty")
    output shouldBe Symbol("empty")
    output shouldBe Symbol("empty")
    parser.commandLine.value shouldBe s"CommandLineProgramTesting --a-string-something Default CommandLineProgramWithOptionSomeDefault --argument ${ReflectionUtil.SpecialEmptyOrNoneToken}"
  }

  it should "return a valid clp when an collection with a default value is set to empty" in {
    Seq(ReflectionUtil.SpecialEmptyOrNoneToken.toUpperCase, ReflectionUtil.SpecialEmptyOrNoneToken.toLowerCase()).foreach { token =>
      val args = Seq[String](nameOf(classOf[CommandLineProgramWithSeqDefault]), "--argument", token)
      val (parser, commandOption, clpOption, output) = TestParseCommandAndSub.parseCommandAndSub[CommandLineProgramTesting, CommandLineProgramWithSeqDefault](args)
      commandOption shouldBe Symbol("defined")
      commandOption.get.getClass shouldBe classOf[CommandLineProgramTesting]
      clpOption shouldBe Symbol("defined")
      clpOption.get.getClass shouldBe classOf[CommandLineProgramWithSeqDefault]
      clpOption.get.argument shouldBe Symbol("empty")
      output shouldBe Symbol("empty")
      output shouldBe Symbol("empty")
      parser.commandLine.value shouldBe s"CommandLineProgramTesting --a-string-something Default CommandLineProgramWithSeqDefault --argument ${ReflectionUtil.SpecialEmptyOrNoneToken}"
    }
  }
  
  "CommandLineParser.clpListUsage" should "throw a BadAnnotationException when a class without the @arg is given" in {
    val parser = new CommandLineParser[Seq[String]]("command-line-name")
    val classes =  Set[Class[_ <: Seq[String]]](classOf[Seq[String]], classOf[List[String]])
    an[BadAnnotationException] should be thrownBy parser.subCommandListUsage(classes, "clp", withPreamble=true)
  }

  private object TestSplitArgs {
    private val parser = new CommandLineParser[CommandLineProgram]("CommandLineName")
    private val packageList = List("com.fulcrumgenomics.sopt.cmdline.testing.clps")
    private val subcommands = Sopt.find[CommandLineProgram](packageList, includeHidden=true)

    def testSplitArgs(args: Seq[String], commandArgsSize: Int, clpArgsSize: Int): Unit = {
      val (commandArgs, clpArgs) = parser.splitArgs(args, subcommands)
      commandArgs should have size commandArgsSize
      clpArgs should have size clpArgsSize
    }
  }

  "CommandLineParser.splitArgs" should "find \"--\" when no command name is given" in {
    import TestSplitArgs._
    testSplitArgs(Seq[String]("--"), 0, 0)
    testSplitArgs(Seq[String]("blah", "--"), 1, 0)
    testSplitArgs(Seq[String]("blah", "--", "blah"), 1, 1)
  }

  it should "find \"--\" first when a command name is also given" in {
    import TestSplitArgs._
    testSplitArgs(Seq[String]("--", nameOf(classOf[CommandLineProgramOne])), 0, 1)
    testSplitArgs(Seq[String](nameOf(classOf[CommandLineProgramOne]), "--"), 1, 0)
    testSplitArgs(Seq[String]("blah", "--", nameOf(classOf[CommandLineProgramOne])), 1, 1)
  }

  it should "find the command name when no \"--\" is given" in {
    import TestSplitArgs._
    testSplitArgs(Seq[String](nameOf(classOf[CommandLineProgramOne])), 0, 1)
    testSplitArgs(Seq[String]("blah", nameOf(classOf[CommandLineProgramOne])), 1, 1)
    testSplitArgs(Seq[String]("blah", nameOf(classOf[CommandLineProgramOne]), "blah"), 1, 2)
  }
}
