package com.fulcrumgenomics.sopt

import com.fulcrumgenomics.sopt.cmdline.{ClpArgumentDefinitionPrinting, ClpGroup, CommandLineParser, CommandLineProgramParser}
import com.fulcrumgenomics.sopt.util.{MarkDownProcessor, ParsingUtil}
import com.sun.org.apache.xpath.internal.Arg

import scala.reflect.runtime.universe.TypeTag
import scala.reflect.ClassTag

/**
  * Facade into Sopt that allows for both parsing of command lines to generate command objects
  * and also inspection of command classes.
  */
object Sopt {
  /** The assumed width of the terminal. */
  val TerminalWidth: Int = 120
  
  /** MarkDown processor used to render text and HTML versions of descriptions. */
  private val markDownProcessor = new MarkDownProcessor(TerminalWidth)

  /** Trait representing the results of trying to parse commands, and sub-commands, in sopt. */
  sealed trait Result[Command,Subcommand]
  /** The result type when a single command is successfully parsed. */
  case class CommandSuccess[A](command: A) extends Result[A,Nothing]
  /** The result type when a command/sub-command pair are successfully parsed. */
  case class SubcommandSuccess[A,B](command: A, subcommand: B) extends Result[A, B]
  /** The result type when a parsing failure occurrs. */
  case class Failure(usage: () => String) extends Result[Nothing,Nothing]

  protected trait MarkDownDescription {
    /** The description as MarkDown text. */
    def description: String
    
    /** Returns the description as plain text, line-wrapped to [[TerminalWidth]]. */
    def descriptionAsText: String = markDownProcessor.toText(markDownProcessor.parse(description)).mkString("\n")

    /** Returns the description as HTML. */
    def descriptionAsHtml: String = markDownProcessor.toHtml(markDownProcessor.parse(description))
  }
  
  /** Represents the group to which command line programs belong. */
  case class Group(name: String, description: String, override val rank: Int) extends ClpGroup
  
  /**
    * Represents the metadata about a command line program that may be consumed externally to generate
    * documentation etc.
    *
    * @param name  the name of the program / command
    * @param group the clp group name of the program
    * @param hidden whether or not the program is marked as hidden
    * @param description the description/documentation for the program in MarkDown format
    * @param args the ordered [[Seq]] of arguments the program takes
    */
  case class ClpMetadata(name: String,
                         group: Group,
                         hidden: Boolean,
                         description: String,
                         args: Seq[Arg]
                        ) extends MarkDownDescription

  /**
    * Represents information about an argument to a command line program.
    *
    * @param name the name of the argument as presented on the command line
    * @param flag the optional flag character for the argument if it has one
    * @param kind the name of the type of the argument (for collection arguments, the type in the collection)
    * @param minValues the minimum number of values that must be specified
    * @param maxValues the maximum number of values that may be specified
    * @param defaultValues the seq of default values, as strings
    * @param sensitive if true the argument is sensitive and values should not be re-displayed
    * @param description the description of the argument
    */
  case class Arg(name: String,
                 flag: Option[Char],
                 kind: String,
                 minValues: Int,
                 maxValues: Int,
                 defaultValues: Seq[String],
                 sensitive: Boolean,
                 description: String
                ) extends MarkDownDescription

  /** Finds classes that extend the given type within the specified packages.
    *
    * @param packages one or more fully qualified packages (e.g. com.fulcrumgenomics.sopt')
    * @param includeHidden whether or not to include programs marked as hidden
    * @tparam A the type of the commands to find
    * @return the resulting set of command classes
    */
  def find[A : ClassTag : TypeTag](packages: Traversable[String], includeHidden: Boolean = false): Seq[Class[_ <: A]] = {
    ParsingUtil.findClpClasses[A](packages.toList, includeHidden=includeHidden).keys.toSeq
  }

  /**
    * Inspect a command class that is annotated with [[clp]] and [[arg]] annotations.
    *
    * @param clp the class to be inspected
    * @tparam A the type of the class
    * @return a metadata object containing information about the command and it's arguments
    */
  def inspect[A](clp: Class[A]): ClpMetadata = {
    val parser = new CommandLineProgramParser(clp, includeSpecialArgs=false)
    val clpAnn = ParsingUtil.findClpAnnotation(clp).getOrElse(throw new IllegalStateException("No @clp on " + clp.getName))
    val args   = parser.argumentLookup.ordered.map ( a => Arg(
      name          = a.longName,
      flag          = a.shortName,
      kind          = a.typeName,
      minValues     = if (a.isCollection) a.minElements else if (a.optional) 0 else 1,
      maxValues     = if (a.isCollection) a.maxElements else 1,
      defaultValues = ClpArgumentDefinitionPrinting.defaultValuesAsSeq(a.defaultValue),
      sensitive     = a.isSensitive,
      description   = a.doc
    ))
    
    val group = clpAnn.group().newInstance()

    ClpMetadata(
      name            = clp.getSimpleName,
      group           = Group(name=group.name, description=group.description, rank=group.rank),
      hidden          = clpAnn.hidden(),
      description     = clpAnn.description().stripMargin.trim().dropWhile(_ == '\n'),
      args            = args
    )
  }

  /**
    * Parses a command line for a single command. Expects that the command name is the first
    * value in the arguments, and that the remainder are arguments to the command.
    *
    * @param name the name of the toolkit, to be printed in usage statements
    * @param args the ordered sequence of arguments from the command line
    * @param commands the set of possible command classes to select from
    * @tparam Command the parent type of all command classes
    * @return the result of parsing the command
    */
  def parseCommand[Command: TypeTag : ClassTag](name: String,
                                                args: Seq[String],
                                                commands: Traversable[Class[_ <: Command]]): Result[_ <: Command,Nothing] = {
    new CommandLineParser[Command](name)parseSubCommand(args, commands)
  }

  /**
    * Parses a command line for a command/sub-command pair. The arguments should contain, in order,
    * any arguments to the primary command, the name of the sub-command, and then the arguments
    * to the sub-command.
    *
    * @param name the name of the toolkit, to be printed in usage statements
    * @param args the ordered sequence of arguments from the command line
    * @param subcommands the set of possible subcommand classes to select from
    * @tparam Command the type of the command class
    * @tparam SubCommand the parent type of all the subcommands
    * @return the result of parsing the command
    */
  def parseCommandAndSubCommand[Command:TypeTag:ClassTag, SubCommand:TypeTag:ClassTag ]
  (name: String, args: Seq[String], subcommands: Traversable[Class[_ <: SubCommand]]): Result[_ <: Command, _ <: SubCommand] = {
    new CommandLineParser[SubCommand](name).parseCommandAndSubCommand[Command](args, subcommands)
  }
}


