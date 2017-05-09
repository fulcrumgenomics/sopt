package com.fulcrumgenomics.sopt

import java.nio.file.Path

import com.fulcrumgenomics.sopt.SoptTest.{FancyPath, PositiveInt}
import com.fulcrumgenomics.sopt.cmdline.Clps
import com.fulcrumgenomics.sopt.util.UnitSpec

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
  @arg(flag='o', doc="Output file.") val outputPath: FancyPath
) extends SoptTestCommand


/** The actual tests. */
class SoptTest extends UnitSpec {
  "Sopt.find" should "find the two concrete subclasses of SoptTestCommand" in {
    val commands = Sopt.find[SoptTestCommand](Seq("com.fulcrumgenomics.sopt"))
    commands should have size 2
    commands should contain theSameElementsAs Seq(classOf[SoptCommand1], classOf[SoptCommand2])
  }
  
  "Sopt.inspect" should "return useful and correct metadata for SoptCommand1" in {
    val clp = Sopt.inspect(classOf[SoptCommand1])
    clp.name              shouldBe "SoptCommand1"
    clp.group             shouldBe new Clps().name
    clp.description       shouldBe "A _first_ test program for Sopt."
    clp.descriptionAsText shouldBe "A first test program for Sopt."
    clp.descriptionAsHtml shouldBe "<p>A <em>first</em> test program for Sopt.</p>"
    clp.hidden            shouldBe false
    
    val args = clp.args.map(a => a.name -> a).toMap
    
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
  }
  
  "Sopt.inspect" should "return useful and correct metadata for SoptCommand2" in {
    val clp = Sopt.inspect(classOf[SoptCommand2])
    clp.name              shouldBe "SoptCommand2"
    clp.group             shouldBe new Clps().name
    clp.description       shouldBe "A second test program for Sopt."
    clp.descriptionAsText shouldBe "A second test program for Sopt."
    clp.descriptionAsHtml shouldBe "<p>A second test program for Sopt.</p>"
    clp.hidden            shouldBe false
    
    val args = clp.args.map(a => a.name -> a).toMap
    
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
  }
}
