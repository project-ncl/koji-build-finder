[1mdiff --git a/core/src/main/java/org/jboss/pnc/build/finder/core/BuildFinder.java b/core/src/main/java/org/jboss/pnc/build/finder/core/BuildFinder.java[m
[1mindex f8fd203..c384426 100644[m
[1m--- a/core/src/main/java/org/jboss/pnc/build/finder/core/BuildFinder.java[m
[1m+++ b/core/src/main/java/org/jboss/pnc/build/finder/core/BuildFinder.java[m
[36m@@ -20,6 +20,8 @@[m [mimport static org.jboss.pnc.build.finder.core.AnsiUtils.red;[m
 [m
 import java.io.File;[m
 import java.io.IOException;[m
[32m+[m[32mimport java.nio.file.Path;[m
[32m+[m[32mimport java.nio.file.Paths;[m
 import java.time.Duration;[m
 import java.time.Instant;[m
 import java.util.ArrayList;[m
[36m@@ -251,19 +253,23 @@[m [mpublic class BuildFinder[m
             Optional<String> rpmFilename = filenames.stream().filter(filename -> filename.endsWith(".rpm")).findFirst();[m
 [m
             if (rpmFilename.isPresent()) {[m
[31m-                String name = rpmFilename.get();[m
[32m+[m[32m                // Let's make sure we have the filename only, removing the parent path[m
[32m+[m[32m                String name = rpmFilename.map(Paths::get).map(Path::getFileName).map(Path::toString).orElse("");[m
[32m+[m
                 KojiNVRA nvra = KojiNVRA.parseNVRA(name);[m
                 KojiIdOrName idOrName = KojiIdOrName.getFor([m
                         nvra.getName() + "-" + nvra.getVersion() + "-" + nvra.getRelease() + "." + nvra.getArch());[m
 [m
                 rpmBuildIdsOrNames.add(idOrName);[m
 [m
[31m-                LOGGER.debug("Added RPM: {}", rpmBuildIdsOrNames.get(rpmBuildIdsOrNames.size() - 1));[m
[32m+[m[32m                LOGGER.info("Added RPM: {}", rpmBuildIdsOrNames.get(rpmBuildIdsOrNames.size() - 1));[m
             }[m
         }[m
 [m
         Future<List<KojiRpmInfo>> futureRpmInfos = pool.submit(() -> session.getRPM(rpmBuildIdsOrNames));[m
         List<KojiRpmInfo> rpmInfos = futureRpmInfos.get();[m
[32m+[m[32m        LOGGER.info("rpmInfos: {}", rpmInfos);[m
[32m+[m
         // XXX: We can't use sorted()/distinct() here because it will cause the lists to not match up with the RPM[m
         // entries[m
         List<KojiIdOrName> rpmBuildIds = rpmInfos.stream()[m
[36m@@ -305,6 +311,10 @@[m [mpublic class BuildFinder[m
             rpmTaskInfos = futureRpmTaskInfos.get();[m
         }[m
 [m
[32m+[m[32m        LOGGER.info("rpmTagInfos: {}", green(rpmTagInfos));[m
[32m+[m[32m        LOGGER.info("rpmRpmInfos: {}", green(rpmRpmInfos));[m
[32m+[m[32m        LOGGER.info("rpmTaskInfos: {}", green(rpmTaskInfos));[m
[32m+[m
         Iterator<KojiTaskInfo> ittasks = rpmTaskInfos.iterator();[m
 [m
         while (it.hasNext()) {[m
[36m@@ -312,18 +322,18 @@[m [mpublic class BuildFinder[m
             Checksum checksum = entry.getKey();[m
             Collection<String> filenames = entry.getValue();[m
 [m
[31m-            LOGGER.debug("After processing, RPM entry has filenames: {}", green(filenames));[m
[32m+[m[32m            LOGGER.info("After processing, RPM entry has filenames: {}", green(filenames));[m
 [m
             KojiRpmInfo rpm = itrpm.next();[m
 [m
[31m-            LOGGER.debug([m
[32m+[m[32m            LOGGER.info([m
                     "Processing checksum: {}, filenames: {}, rpm: {}",[m
                     green(checksum),[m
                     green(filenames),[m
                     green(rpm));[m
 [m
             if (rpm == null) {[m
[31m-                LOGGER.debug("Got null RPM for checksum: {}, filenames: {}", green(checksum), green(filenames));[m
[32m+[m[32m                LOGGER.info("Got null RPM for checksum: {}, filenames: {}", green(checksum), green(filenames));[m
                 markNotFound(entry);[m
                 continue;[m
             } else if (rpm.getBuildId() == null) {[m
[36m@@ -346,6 +356,7 @@[m [mpublic class BuildFinder[m
 [m
                 continue;[m
             }[m
[32m+[m[32m            LOGGER.info("rpm.getPayloadhash(): {}", green(rpm.getPayloadhash()));[m
 [m
             // XXX: Only works for md5, and we can't look up RPMs by checksum[m
             // XXX: We can use other APIs to get other checksums, but they are not cached as part of this object[m
[36m@@ -1207,6 +1218,9 @@[m [mpublic class BuildFinder[m
 [m
     public void outputToFile() throws IOException {[m
         JSONUtils.dumpObjectToFile(builds, new File(outputDirectory, getBuildsFilename()));[m
[32m+[m[32m        JSONUtils.dumpObjectToFile(foundChecksums, new File(outputDirectory, "foundChecksums.json"));[m
[32m+[m[32m        JSONUtils.dumpObjectToFile(notFoundChecksums, new File(outputDirectory, "notFoundChecksums.json"));[m
[32m+[m[32m        JSONUtils.dumpObjectToFile(analyzer.getFiles(), new File(outputDirectory, "inverse_map.json"));[m
     }[m
 [m
     @Override[m
