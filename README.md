[![Build Status](https://github.com/fulcrumgenomics/sopt/workflows/unit%20tests/badge.svg)](https://github.com/fulcrumgenomics/sopt/actions?query=workflow%3A%22unit+tests%22)
[![Coverage Status](https://codecov.io/github/fulcrumgenomics/sopt/coverage.svg?branch=main)](https://codecov.io/github/fulcrumgenomics/sopt?branch=main)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.fulcrumgenomics/sopt_2.12/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.fulcrumgenomics/sopt_2.12)
[![Javadocs](http://javadoc.io/badge/com.fulcrumgenomics/sopt_2.12.svg)](http://javadoc.io/doc/com.fulcrumgenomics/sopt_2.12)
[![License](http://img.shields.io/badge/license-MIT-blue.svg)](https://github.com/fulcrumgenomics/sopt/blob/main/LICENSE)
[![Language](http://img.shields.io/badge/language-scala-brightgreen.svg)](http://www.scala-lang.org/)

# sopt - Scala Option Parsing Library

_sopt_ is a scala library for command line option parsing with minimal dependencies.  It is designed for toolkits that have multiple "commands" such as [dagr](https://github.com/fulcrumgenomics/dagr) and [fgbio](https://github.com/fulcrumgenomics/fgbio).  The latest API documentation can be found [here](http://javadoc.io/doc/com.fulcrumgenomics/sopt_2.12).

<p>
<a href float="left"="https://fulcrumgenomics.com"><img src=".github/logos/fulcrumgenomics.svg" alt="Fulcrum Genomics" height="100"/></a>
</p>

[Visit us at Fulcrum Genomics](www.fulcrumgenomics.com) to learn more about how we can power your Bioinformatics with sopt and beyond.

<a href="mailto:contact@fulcrumgenomics.com?subject=[GitHub inquiry]"><img src="https://img.shields.io/badge/Email_us-brightgreen.svg?&style=for-the-badge&logo=gmail&logoColor=white"/></a>
<a href="https://www.fulcrumgenomics.com"><img src="https://img.shields.io/badge/Visit_Us-blue.svg?&style=for-the-badge&logo=wordpress&logoColor=white"/></a>

_sopt_ has the following high level features:

- Support for GNU/posix style argument names and conventions
  - Camel case names in scala are auto-translated to GNU style options (e.g. `inputFile -> input-file`)
- Argument configuration via annotations on scala classes
- Reflectively builds typed strongly typed argument values for most simple types:
    -   Primitive types like `Int` and `Double`
    -   Any type that has a constructor from `String` or a companion `apply(String)`
    -   Option types, e.g. `Option[Int]` or `Option[String]`, etc.
    -   Collection types, e.g. `Seq[Int]` or `Set[String`], etc.
- Typedefs used for argument types and will be used in usage & documentation
- Argument file support similar to Python's Argparse
- Command usage and argument documentation in [GitHub flavored MarkDown](https://guides.github.com/features/mastering-markdown/), formatted appropriately for the terminal
- Terminal output using ANSI escape sequences to color and highlight usage
- APIs to retrieve command and argument metadata, e.g. to create offline documentation

## Getting Started
To use sopt you will need to add it to your build.  For sbt this looks like:

```scala
libraryDependencies +=  "com.fulcrumgenomics" %% "sopt" % "1.1.0"
```

You'll then need the following:

1. A `trait` which all your commands or tools will extend
1. One or more command classes
1. An `object` with a main method that invokes `sopt` to parse the command line

### A Short Example

The following is a minimal example of a use of sopt.

```scala
package example

import com.fulcrumgenomics.sopt._
import com.fulcrumgenomics.sopt.Sopt._
import example.Types._

/** All command classes exposed on the command-line will extend or mix-in this trait. */
trait Tool { def execute(): Unit }

object Types {
  type Name = String
}

/** An example command. */
@clp(description=
    """
     |An example program that greets people. The `greeting` argument is optional
     |since it has a default.
    """)
class Greet(
  @arg(flag='g', doc="The greeting.")   val greeting: String = "Hello",
  @arg(flag='n', doc="Someone's name.") val name: Name
) extends Tool {
  override def execute(): Unit = System.out.println(s"$greeting $name!")
}

/** The main class that invokes sopt. */
object Main {
  def main(args: Array[String]): Unit = {
    val commands = Sopt.find[Tool](packages=Seq("example"))
    Sopt.parseCommand[Tool](name="example-kit", args=args, commands=commands) match {
      case Failure(usage) => 
        System.err.print(usage())
        System.exit(1)
      case CommandSuccess(tool) => 
        tool.execute()
        System.exit(0)
    }
  }
}
```

The `Tool` trait is not required, but it is generally useful to have a trait which is implemented by all commands, so that not only can they have a common method (ex. `execute()`) that can be invoked to _do something_, the available commands can be found using the `Tool` trait and `Sopt.find`.  Alternatively you could invoke `Sopt.parseCommand[AnyRef]` directly!  To do so, you would need to manually construct the list of commands instead of searching for subclasses with `Sopt.find[AnyRef](packages)`.

In this example, the `Main` class is implemented only once and can act as a dispatcher to any number of commands that implement `Tool`.  It also acts as a central point to add consistent behaviour amongst tools, for example printing out the date and time at the start and end of execution.

Running the `Main` class from the command line yields the following output:

```
USAGE: example-kit [command] [arguments]
Version: 1.0
-----------------------------------------------------------------------------

Available Sub-Commands:
-----------------------------------------------------------------------------
Clps:                                 Various command line programs.
    GreetingCommand                    An example program that greets people.
-----------------------------------------------------------------------------

No sub-command given.
```

Running the `Main` class and specifying the `Greet` command without any arguments produces the following output:

```
USAGE: Greet [arguments]
Version: 0.2.0-SNAPSHOT
------------------------------------------------------------------------------------------------
An example program that greets people. The greeting argument is optional since it has a default.

Greet Required Arguments:
------------------------------------------------------------------------------------------------
-n String, --name=Name      Someone's name.

Greet Optional Arguments:
------------------------------------------------------------------------------------------------
-h [true|false], --help[=true|false]
                              Display the help message. Default: false.
-g String, --greeting=String  The greeting. Default: Hello.
--version[=true|false]        Display the version number for this tool. Default: false.

Error: Argument 'name' is required.
```

## Documentation

API documentation for all versions can be viewed on [javadoc.io](http://www.javadoc.io/doc/com.fulcrumgenomics/sopt_2.12/0.2.0).
 
## Argument Naming & Formatting

Each argument may have a long name and optionally a short name.

### Short Names

Short names are single-character names. Arguments with short names may be formatted on the command line as follows:

- `-<name><value>`
- `-<name>=<value>`
- `-<name> <value>`

### Long names

Long names may be of any length and are generally specified as one or more lower-case words separated with hyphens (e.g. `--input-files`).  Long names may be used on the command line with the following formats:

- `--<name>=<value>`
- `--<name> <value>`

Name must be `[A-Za-z0-9?][-A-Za-Z0-9?]*`

The following is not supported:

- `--<name><value>`

due to the prefix support described below.

### Prefixes

Prefixes of option names are supported, as long as the a prefix is unambiguous, and either a ` `(whitespace) or `=` delimiter is used.

In a command with no other options beginning with `foo` the following are equivalent:

- `--foobar <value>`
- `--fooba  <value>`
- `--foob  <value>`
- `--foo  <value>`

But if we have another option, e.g. `--football <value>`, then using `--foo <value>` will cause a  `DuplicateOptionNameException` to be thrown.

## Argument Types

### Flags

Flags are arguments with a boolean (true or false) value. The value may be ommitted, in which case it is interpreted as if `true` were provided. The following are all equivalent:

- `-x`
- `-xtrue`
- `-x=true`
- `-xT`
- `-x=T`

`Yes` and `Y` are also interpreted as `true`; `No` and `N` are interpreted as `false`.  Both are case insensitive.

### Single-value arguments

Single value arguments may only be specified once on the command line. If the argument has a default value, the value from the command line will override it.  Single-value arguments may be specified using long or short names, in the following ways:

- `-x <value>`
- `-x<value>`
- `-x=<value>`
- `--extra <value>`
- `--extra=<value>`

### Multi-value arguments

Arguments that accept multiple values may be specified up to the maximum number of times specified by the tool.  If an argument has a default value, the value(s) specified on the command line replace the default value (i.e. they do not add to it).

Multiple values can specified via multiple name/value pairs, e.g.:

- `--input=foo.txt --input=bar.txt --input=whee.txt`
- `--input foo.txt --input bar.txt --input whee.txt`

or with multiple values following a single argument name:

- `--input foo.txt bar.txt whee.txt`
- `--input *.txt`

### Optional arguments

Arguments are optional on the command line if any of the following are true:

- The argument has a default value
- The argument's type is `Option[_]`
- The argument's type is a collection type (e.g. `Seq`) and `minElements` is set to 0

`Option` and collection arguments that are optional, but have default values may be cleared by specifying the special argument value `:none:`.

### Argument files

Argument files are modeled after the implementation in [Python's Argparse library](https://docs.python.org/3/library/argparse.html). In short:

- Any argument prefixed with `@` is expected to be an argument file (e.g. `-foo=bar @whee.txt` species one parameter on the command line, and then a file called `whee.txt`)
- The arguments present in an argument file are substituted into the command line at the point where the argument file is specified
- Each line in the argument file is treated as a single token or argument

The latter is especially useful when passing many arguments that include spaces or special characters.  For example the following file:

```
--funny-strings
Hello World!
I'm a shooting *star*
```

is equivalent to `--funny-strings 'Hello World!' 'I\'m a shooting *star*'`.
