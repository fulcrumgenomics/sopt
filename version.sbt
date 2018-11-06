val gitHeadCommitSha = settingKey[String]("current git commit SHA")
gitHeadCommitSha in ThisBuild := scala.sys.process.Process("git rev-parse --short HEAD").lineStream.head

// *** IMPORTANT ***
// One of the two "version" lines below needs to be uncommented.
// version in ThisBuild := "0.6.1" // the release version
version in ThisBuild := s"0.7.0-${gitHeadCommitSha.value}-SNAPSHOT" // the snapshot version
