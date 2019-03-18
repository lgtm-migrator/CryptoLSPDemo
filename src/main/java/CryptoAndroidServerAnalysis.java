import com.ibm.wala.classLoader.Module;
import de.upb.soot.core.SootClass;
import de.upb.soot.frontends.java.JimpleConverter;
import de.upb.soot.frontends.java.WalaClassLoader;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.logging.Logger;
import magpiebridge.core.AnalysisResult;
import magpiebridge.core.IProjectService;
import magpiebridge.core.JavaProjectService;
import magpiebridge.core.MagpieServer;
import magpiebridge.core.ServerAnalysis;
import soot.PackManager;
import soot.Scene;
import soot.Transform;
import soot.Transformer;
import soot.jimple.infoflow.InfoflowConfiguration;
import soot.jimple.infoflow.InfoflowConfiguration.PathReconstructionMode;
import soot.jimple.infoflow.android.InfoflowAndroidConfiguration;
import soot.jimple.infoflow.android.SetupApplication;
import soot.jimple.infoflow.android.config.SootConfigForAndroid;
import soot.jimple.infoflow.taintWrappers.EasyTaintWrapper;
import soot.options.Options;

public class CryptoAndroidServerAnalysis implements ServerAnalysis {

  private static final Logger LOG = Logger.getLogger("main");

  private String ruleDirPath;
  private String configPath;
  private String androidPlatform;

  public CryptoAndroidServerAnalysis(
      String ruleDirPath, String configPath, String androidPlatform) {
    this.ruleDirPath = ruleDirPath;
    this.configPath = configPath;
    this.androidPlatform = androidPlatform;
  }

  @Override
  public String source() {
    return "CogniCrypt";
  }

  @Override
  public void analyze(Collection<Module> files, MagpieServer server) {
    String apkFile = "";
    Set<String> libPath = new HashSet<>();
    Set<String> srcPath = null;
    Optional<IProjectService> opt = server.getProjectService("java");
    if (opt.isPresent()) {
      JavaProjectService ps = (JavaProjectService) server.getProjectService("java").get();
      Set<Path> sourcePath = ps.getSourcePath();
      if (!sourcePath.isEmpty()) {
        Set<String> temp = new HashSet<>();
        sourcePath.stream().forEach(path -> temp.add(path.toString()));
        srcPath = temp;
      }
    }
    Collection<AnalysisResult> results = Collections.emptyList();
    if (srcPath != null) {
      // do whole program analysis
      results = analyze(androidPlatform, apkFile, srcPath, libPath);
    } else {
      // only analyze relevant files
      // results = analyze(files);
    }
    for (AnalysisResult re : results) {
      System.err.println(re.toString());
    }
    server.consume(results, source());
  }

  public void buildCallGraphWithFlowDroid(
      String androidPlatform, String apkFile, Set<String> srcPath, Set<String> libPath) {
    try {
      // setup flowDroid configuration
      InfoflowAndroidConfiguration c = new InfoflowAndroidConfiguration();
      // c.setWriteOutputFiles(true);
      c.getPathConfiguration()
          .setPathReconstructionMode(
              PathReconstructionMode.Fast); // turn on to compute data flow path
      c.getAnalysisFileConfig().setAndroidPlatformDir(androidPlatform);
      c.getAnalysisFileConfig().setTargetAPKFile(apkFile);
      SetupApplication flowDroid = new SetupApplication(c);
      flowDroid.setSourceCodePath(srcPath);
      flowDroid.setLibPath(libPath);
      String androidJar = Scene.v().getAndroidJarPath(androidPlatform, apkFile);
      flowDroid.setAndroidJar(androidJar);
      Consumer<Set<String>> sourceCodeConsumer =
          sourceCodePath -> {
            HashSet<String> libs = new HashSet<>(libPath);
            libs.add(androidJar);
            WalaClassLoader loader = new WalaClassLoader(sourceCodePath, libs, null);
            List<SootClass> sootClasses = loader.getSootClasses();
            JimpleConverter jimpleConverter = new JimpleConverter(sootClasses);
            jimpleConverter.convertAllClasses();
          };
      flowDroid.setSourceCodeConsumer(sourceCodeConsumer);
      flowDroid.setCallbackFile(configPath + File.separator + "AndroidCallbacks.txt");
      flowDroid.setTaintWrapper(
          new EasyTaintWrapper(configPath + File.separator + "EasyTaintWrapperSource.txt"));
      SootConfigForAndroid sootConfigForAndroid =
          new SootConfigForAndroid() {
            @Override
            public void setSootOptions(Options options, InfoflowConfiguration config) {
              options.set_exclude(Collections.emptyList());
              Options.v()
                  .set_ignore_resolving_levels(
                      true); // important, otherwise android classes can't be loaded.
            }
          };
      flowDroid.setSootConfig(sootConfigForAndroid);
      flowDroid.constructCallgraph();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public Collection<AnalysisResult> analyze(
      String androidPlatform, String apkFile, Set<String> srcPath, Set<String> libPath) {
    buildCallGraphWithFlowDroid(androidPlatform, apkFile, srcPath, libPath);
    CryptoTransformer transformer = new CryptoTransformer(ruleDirPath, true);
    runSootPacks(transformer);
    Collection<AnalysisResult> results = transformer.getAnalysisResults();
    return results;
  }

  private void runSootPacks(Transformer t) {
    PackManager.v().getPack("wjtp").add(new Transform("wjtp.cognicrypt", t));
    PackManager.v().getPack("wjtp").apply();
  }
}
