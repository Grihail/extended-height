# Consumer test (plan step 2)

A minimal mod that uses ExtendedHeight only through its public API, like a generator mod would: it registers the
world type `ehconsumer` with -256..319 and, when a server starts with `level-type=ehconsumer`, checks the range and sets
and reads blocks from y -256 to 319 (works only if the library's mixins were applied), writes `consumer-result.txt`
and stops the server.

```shell
# in the library project: build and install the artifacts into mavenLocal
gradlew build publishToMavenLocal
# dev environment of this project (library from mavenLocal, :api for compilation, :dev at runtime)
gradlew -p tools/consumer-test runServer       # result: tools/consumer-test/run/server/consumer-result.txt
# obfuscated environment (this mod's reobf jar + extendedheight-1.0.0.jar + UniMixins in run/obf-server/mods)
gradlew -p tools/consumer-test runObfServer    # result: tools/consumer-test/run/obf-server/consumer-result.txt
```

`run/server` and `run/obf-server` need `eula.txt` (`eula=true`) and `server.properties` with `level-type=ehconsumer`
and `online-mode=false`.
