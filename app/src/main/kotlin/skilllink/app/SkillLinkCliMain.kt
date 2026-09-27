package skilllink.app

fun main(args: Array<String>) {
  val runtime = SkillLinkRuntimeFactory.create()
  val rendered = runtime.cli.run(args)
  if (rendered.stdout.isNotEmpty()) {
    print(rendered.stdout)
  }
  if (rendered.stderr.isNotEmpty()) {
    System.err.print(rendered.stderr)
  }
  kotlin.system.exitProcess(rendered.exitCode)
}
