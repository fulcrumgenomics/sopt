package com.fulcrumgenomics.sopt.util

class MarkDownProcessorTest extends UnitSpec {
  val processor = new MarkDownProcessor()

  def toText(doc: String): String = processor.toText(processor.parse(doc)).mkString("\n")


  "MarkDownProcessor.toTree" should "print the tree structure of a simple document" in {
    val doc = processor.parse(
      """
        |A paragraph
        |
        |1. An ordered list item
        |  - An unordered list item
      """.stripMargin)
    val tree = processor.toTree(doc)

    val expected =
      """
        |Document
        |  Paragraph
        |    Text
        |  OrderedList
        |    OrderedListItem
        |      Paragraph
        |        Text
        |  BulletList
        |    BulletListItem
        |      Paragraph
        |        Text
      """.stripMargin

    tree.trim shouldBe expected.trim
  }

  "MarkDownProcessor.toText" should "emit a simple paragraph" in {
    toText("Hello World!") shouldBe "Hello World!"
  }

  it should "unwrap overly wrapped text" in {
    toText("Hello\nBrave\nWorld!") shouldBe "Hello Brave World!"
  }

  it should "emit an ordered list with correct numbering" in {
    toText("1. Hello\n 1. Goodbye\n 1. Whatever") shouldBe "  1. Hello\n  2. Goodbye\n  3. Whatever"
  }

  it should "emit an unordered list" in {
    toText("- Hello\n- Who are you?\n- So Long!") shouldBe "  * Hello\n  * Who are you?\n  * So Long!"
  }

  it should "remove inline formatting characters and re-wrap appropriately" in {
    val markdown = "This _is_ some text that's **over** `80` chars *long* with formatting but fits without."
    val expected = "This is some text that's over '80' chars long with formatting but fits without."
    toText(markdown) shouldBe expected
  }

  it should "reformat links sensibly" in {
    val markdown = "See the [readme](../Readme.md) for the project."
    val expected = "See the readme (../Readme.md) for the project."
    toText(markdown) shouldBe expected
  }

  it should "re-layout hard-wrapped lines but not merge paragraphs" in {
    val markdown =
      """
        |This is a 
        |silly little
        |paragraph!
        |
        |```scala
        |val foo = 2
        |val bar = 2 * foo
        |```
        |
        |This is another paragraph
        |that is wrapped.
        |
        |And one last paragraph.
      """.stripMargin

    val expected =
      """
        |This is a silly little paragraph!
        |
        |  val foo = 2
        |  val bar = 2 * foo
        |
        |This is another paragraph that is wrapped.
        |
        |And one last paragraph.
      """.stripMargin

    toText(markdown) shouldBe expected.trim
  }
  
  it should "create underlined headings" in {
    val markdown = 
      """
        |# Heading 1
        |
        |## Second level heading
        |
        |### Third is the best!
      """.stripMargin
    
    val expected = 
      """
        |Heading 1
        |=========
        |
        |Second level heading
        |--------------------
        |
        |Third is the best!
        |------------------
      """.stripMargin
    
    toText(markdown) shouldBe expected.trim
  }
  
  it should "correctly display a block-quote" in {
    val markdown =
      """
        |> this is a fairly long block quote that will need
        |> to be re-wrapped but ultimately will display over
        |> multiple lines anyway.
      """.stripMargin
    
    val expected =
      """
        |> this is a fairly long block quote that will need to be re-wrapped but
        |> ultimately will display over multiple lines anyway.
      """.stripMargin
    
    toText(markdown) shouldBe expected.trim
  }
  
  it should "respect the line width and indent" in {
    val proc = new MarkDownProcessor(lineLength=40, indentSize=4)
    val markdown =
      """
        |1. This is a fairly simple ordered list with some items in it
        |    1. A sub-list should re-start at one
        |    1. So there's that
        |        1. And again!  This is going to wrap nicely I think!
        |1. And back out again    
      """.stripMargin
    
    val expected =
      """
        |    1. This is a fairly simple ordered
        |       list with some items in it
        |        1. A sub-list should re-start
        |           at one
        |        2. So there's that
        |            1. And again! This is going
        |               to wrap nicely I think!
        |    2. And back out again
      """.trim.stripMargin
    
    val actual = proc.toText(proc.parse(markdown)).mkString("\n")
    actual shouldBe expected
  }

  it should "handle markdown with words longer than line length" in {
    val proc = new MarkDownProcessor(lineLength=40, indentSize=4)
    val markdown =
      """
        |This is a line.
        |ThisIsALineWithNoSpacesThatIsFarTooLongButShouldStillBeEmittedOnASingleLineAndNotFail
        |This is another line.
      """.trim.stripMargin

    proc.toText(proc.parse(markdown)) shouldBe markdown.linesIterator.toSeq
  }

  "MarkDownProcessor.toText" should "convert markdown to text ignoring trailing terminal codes" in {
    val proc = new MarkDownProcessor(lineLength=40)
    val sb = new StringBuilder()
    sb.append(KGRN("--remove-alignment-information[[=true|false]]"))
    sb.append(" ")
    sb.append(KCYN("Remove all alignment information (as well as secondary and supplementary records. " + KGRN("[[Default: false]].")))
    val markdown = KCYN(sb.toString)
    val lines = proc.toText(proc.parse(markdown))
    val processedMarkdown = markdown.replaceAll("\\[\\[", "[").replaceAll("\\]\\]", "]")
    lines.mkString(" ") shouldBe processedMarkdown
  }

  "MarkDownProcessor.toHtml" should "convert markdown to HTML" in {
    val markdown =
      """
        |# Hello
        |
        |This is a paragraph!
      """.stripMargin
    
    val expected =
      """
        |<h1>Hello</h1>
        |<p>This is a paragraph!</p>
      """.stripMargin.trim
    
    val html = processor.toHtml(processor.parse(markdown)).trim
    html shouldBe expected
  }

  "MarkDownProcessor.trim" should "trim the first non-ansi-escape-code non-whitespace characters" in {
    MarkDownProcessor.trim(KRED(KGRN(" A BCDEF G"))) shouldBe KRED(KGRN("A BCDEF G"))
    MarkDownProcessor.trim(KRED(KGRN("A BCDEF G"))) shouldBe KRED(KGRN("A BCDEF G"))
    MarkDownProcessor.trim(KRED(" ABCDEFG")) shouldBe KRED("ABCDEFG")
    MarkDownProcessor.trim(" ABCDEFG") shouldBe "ABCDEFG"
  }

  it should "trim the last non-ansi-escape-code whitespace characters" in {
    MarkDownProcessor.trim(KRED(KGRN("A BCDEF G "))) shouldBe KRED(KGRN("A BCDEF G"))
    MarkDownProcessor.trim(KRED(KGRN("A BCDEF G"))) shouldBe KRED(KGRN("A BCDEF G"))
    MarkDownProcessor.trim(KRED("ABCDEFG ")) shouldBe KRED("ABCDEFG")
    MarkDownProcessor.trim("ABCDEFG ") shouldBe "ABCDEFG"
  }
}
