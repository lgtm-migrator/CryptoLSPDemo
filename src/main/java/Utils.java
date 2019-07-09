import java.io.File;
import java.util.Set;
import org.apache.commons.lang.ArrayUtils;
import soot.Main;

public class Utils {

  public static String ruleDirPath = new File("config/JCA_rules").getAbsolutePath();
  public static String configPath = new File("config").getAbsolutePath();

  public static void generateJar(
      String apk, String androidPlatform, String outputDir, Set<String> excludedClasses) {
    String[] args = {
      "-process-dir",
      apk,
      "-src-prec",
      "apk",
      "-android-jars",
      androidPlatform,
      "-output-format",
      "class",
      "-allow-phantom-refs",
      "-output-jar",
      "-output-dir",
      outputDir
    };
    String[] exclude = new String[excludedClasses.size() * 2];
    int i = 0;
    for (String pkg : excludedClasses) {
      exclude[i] = "-exclude";
      exclude[i + 1] = pkg;
      i = i + 2;
    }
    String[] ops = (String[]) ArrayUtils.addAll(args, exclude);
    Main.main(ops);
  }
}
