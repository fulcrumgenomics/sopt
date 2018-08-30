package com.fulcrumgenomics.sopt

import java.nio.file.Path

import com.fulcrumgenomics.sopt.Sopt._
import com.fulcrumgenomics.sopt.SoptTest.{FancyPath, PositiveInt}
import com.fulcrumgenomics.sopt.cmdline.{Clps, CommandLineParser}
import com.fulcrumgenomics.sopt.util.UnitSpec
import org.scalatest.OptionValues

/** Some types used to test returning of typedef vs. underlying types. */
object SoptTest {
  type FancyPath = Path
  type PositiveInt = Int
} 

/** A trait so we can search for your the subclasses for this test. */
trait SoptTestCommand

/** A small CLP */
@clp(group=classOf[Clps], description="A _first_ test program for Sopt.")
class SoptCommand1 
( @arg(flag='i', doc="**Input** file.")  val input: FancyPath,
  @arg(flag='o', doc="Output file.") val output: Option[FancyPath],
  @arg(flag='n', doc="Numbers.", minElements=0, maxElements=100) val numbers: Seq[PositiveInt] = Seq(1, 2, 3)
) extends SoptTestCommand

/** A small CLP */
@clp(group=classOf[Clps], description="A second test program for Sopt.")
class SoptCommand2 
( @arg(flag='i', doc="Input file.") val inputPath: FancyPath,
  @arg(flag='o', doc="Output file.") val outputPath: FancyPath,
  nonArgArgument: String = "Shhh, I'm not really here",
  val nonArgArgument2: String = "Me either!",
  private val nonArgArgument3: String = "Nobody here but us @args"
) extends SoptTestCommand


/** The actual tests. */
class SoptTest extends UnitSpec with OptionValues {
  "Sopt.find" should "find the two concrete subclasses of SoptTestCommand" in {
    val commands = Sopt.find[SoptTestCommand](Seq("com.fulcrumgenomics.sopt"))
    commands should have size 2
    commands should contain theSameElementsAs Seq(classOf[SoptCommand1], classOf[SoptCommand2])
  }

  private def compareUserValues(actual: Option[Seq[String]], expected: Option[Seq[String]]): Unit = {
    (actual, expected) match {
      case (Some(act), Some(exp)) => act should contain theSameElementsInOrderAs exp
      case (act, exp)             => act shouldBe exp // this should fail if both aren't None
    }
  }
  
  "Sopt.inspect" should "return useful and correct metadata for SoptCommand1" in {
    val clpDefault = Sopt.inspect(classOf[SoptCommand1])
    val clpValues  = {
      // NB: subcommandMetadata calls Sopt.inspect
      val parser = new CommandLineParser[SoptCommand1]("SoptCommand1")
      val inArgs = Seq("SoptCommand1", "--input", "/some/input", "--output", "/some/output")
      val result = parser.parseSubCommand(args=inArgs, subcommands=Seq(classOf[SoptCommand1]))
      result.isInstanceOf[CommandSuccess[_]] shouldBe true
      parser.subcommandMetadata.value
    }

    val clpData = Seq(
      (clpDefault, None, None, None),
      (clpValues, Some(Seq("/some/input")), Some(Seq("/some/output")), None)
    )

    clpData.foreach { case (clp: ClpMetadata, input, output, numbers) =>
      clp.name              shouldBe "SoptCommand1"
      clp.group             shouldBe Group(new Clps().name, new Clps().description, new Clps().rank)
      clp.description       shouldBe "A _first_ test program for Sopt."
      clp.descriptionAsText shouldBe "A first test program for Sopt."
      clp.descriptionAsHtml shouldBe "<p>A <em>first</em> test program for Sopt.</p>"
      clp.hidden            shouldBe false

      val args = clp.args.map(a => a.name -> a).toMap


      if (clp.args.exists(_.userValue.nonEmpty)) {
        args.size shouldBe 5 // +2 special arguments (version/help)
        val userCommandLine = s"SoptCommand1 --input ${input.value.head} --output ${output.value.head}"
        clp.commandLine(withDefaults=true) shouldBe s"$userCommandLine --numbers 1 --numbers 2 --numbers 3 --help false --version false"
        clp.commandLine(withDefaults=false) shouldBe userCommandLine
      }
      else {
        args.size shouldBe 3
        clp.commandLine(withDefaults=true) shouldBe "SoptCommand1 --input <FancyPath> --output <FancyPath> --numbers 1 --numbers 2 --numbers 3"
        clp.commandLine(withDefaults=false) shouldBe s"SoptCommand1"
      }

      args("input").name              shouldBe "input"
      args("input").flag              shouldBe Some('i')
      args("input").kind              shouldBe "FancyPath"
      args("input").defaultValues     shouldBe Seq.empty
      args("input").description       shouldBe "**Input** file."
      args("input").descriptionAsText shouldBe "Input file."
      args("input").descriptionAsHtml shouldBe "<p><strong>Input</strong> file.</p>"
      args("input").sensitive         shouldBe false
      args("input").minValues         shouldBe 1
      args("input").maxValues         shouldBe 1
      compareUserValues(args("input").userValue, input)

      args("output").name              shouldBe "output"
      args("output").flag              shouldBe Some('o')
      args("output").kind              shouldBe "FancyPath"
      args("output").defaultValues     shouldBe Seq.empty
      args("output").description       shouldBe "Output file."
      args("output").descriptionAsText shouldBe "Output file."
      args("output").descriptionAsHtml shouldBe "<p>Output file.</p>"
      args("output").sensitive         shouldBe false
      args("output").minValues         shouldBe 0
      args("output").maxValues         shouldBe 1
      compareUserValues(args("output").userValue, output)

      args("numbers").name              shouldBe "numbers"
      args("numbers").flag              shouldBe Some('n')
      args("numbers").kind              shouldBe "PositiveInt"
      args("numbers").defaultValues     shouldBe Seq("1", "2", "3")
      args("numbers").description       shouldBe "Numbers."
      args("numbers").descriptionAsText shouldBe "Numbers."
      args("numbers").descriptionAsHtml shouldBe "<p>Numbers.</p>"
      args("numbers").sensitive         shouldBe false
      args("numbers").minValues         shouldBe 0
      args("numbers").maxValues         shouldBe 100
      compareUserValues(args("numbers").userValue, numbers)
    }
  }
  
  it should "return useful and correct metadata for SoptCommand2" in {
    val clpDefault = Sopt.inspect(classOf[SoptCommand2])
    val clpValues  = {
      // NB: subcommandMetadata calls Sopt.inspect
      val parser = new CommandLineParser[SoptCommand2]("SoptCommand2")
      val inArgs = Seq("SoptCommand2", "--input", "/some/input", "--output", "/some/output")
      val result = parser.parseSubCommand(args=inArgs, subcommands=Seq(classOf[SoptCommand2]))
      result.isInstanceOf[CommandSuccess[_]] shouldBe true
      parser.subcommandMetadata.value
    }
    val clpData = Seq(
      (clpDefault, None, None),
      (clpValues, Some(Seq("/some/input")), Some(Seq("/some/output")))
    )

    clpData.foreach { case (clp: ClpMetadata, input, output) =>

      clp.name              shouldBe "SoptCommand2"
      clp.group             shouldBe Group(new Clps().name, new Clps().description, new Clps().rank)
      clp.description       shouldBe "A second test program for Sopt."
      clp.descriptionAsText shouldBe "A second test program for Sopt."
      clp.descriptionAsHtml shouldBe "<p>A second test program for Sopt.</p>"
      clp.hidden            shouldBe false

      val args = clp.args.map(a => a.name -> a).toMap

      if (clp.args.exists(_.userValue.nonEmpty)) {
        args.size shouldBe 4 // +2 special arguments (version/help)
        val userCommandLine = s"SoptCommand2 --input-path ${input.value.head} --output-path ${output.value.head}"
        clp.commandLine(withDefaults=true) shouldBe s"$userCommandLine --help false --version false"
        clp.commandLine(withDefaults=false) shouldBe userCommandLine
      }
      else {
        args.size shouldBe 2
        clp.commandLine(withDefaults=true) shouldBe "SoptCommand2 --input-path <FancyPath> --output-path <FancyPath>"
        clp.commandLine(withDefaults=false) shouldBe s"SoptCommand2"
      }

      args("input-path").name              shouldBe "input-path"
      args("input-path").flag              shouldBe Some('i')
      args("input-path").kind              shouldBe "FancyPath"
      args("input-path").defaultValues     shouldBe Seq.empty
      args("input-path").description       shouldBe "Input file."
      args("input-path").descriptionAsText shouldBe "Input file."
      args("input-path").descriptionAsHtml shouldBe "<p>Input file.</p>"
      args("input-path").sensitive         shouldBe false
      args("input-path").minValues         shouldBe 1
      args("input-path").maxValues         shouldBe 1
      compareUserValues(args("input-path").userValue, input)

      args("output-path").name              shouldBe "output-path"
      args("output-path").flag              shouldBe Some('o')
      args("output-path").kind              shouldBe "FancyPath"
      args("output-path").defaultValues     shouldBe Seq.empty
      args("output-path").description       shouldBe "Output file."
      args("output-path").descriptionAsText shouldBe "Output file."
      args("output-path").descriptionAsHtml shouldBe "<p>Output file.</p>"
      args("output-path").sensitive         shouldBe false
      args("output-path").minValues         shouldBe 1
      args("output-path").maxValues         shouldBe 1
      compareUserValues(args("output-path").userValue, output)
    }
  }
}