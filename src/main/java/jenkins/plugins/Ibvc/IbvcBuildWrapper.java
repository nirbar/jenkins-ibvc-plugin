/**
 * 
 */
package jenkins.plugins.Ibvc;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import hudson.AbortException;
import hudson.EnvVars;
import hudson.Extension;
import hudson.Launcher;
import hudson.Launcher.ProcStarter;
import hudson.Proc;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Build;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.model.Descriptor.FormException;
import hudson.scm.SCM;
import hudson.scm.SCMDescriptor;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

/**
 * @author Build
 *
 */
public class IbvcBuildWrapper extends BuildWrapper {

    private final String ibvcConfig_;
    private final String sfvcRevision_;
    private final String addiotinalArguments_;
    private final Collection<IbvcParameter> parameters_;
    
    private String bestMatchSfvcRev_ = null;
    private String bestMatchIbvcRev_ = null;

    @DataBoundConstructor
    public IbvcBuildWrapper(
        String ibvcConfig
		, String sfvcRevision
		, String addiotinalArguments
		, Collection<IbvcParameter> parameters
		) {
    	if(ibvcConfig.length() > 0){
		    File file1 = new File(ibvcConfig);
		    ibvcConfig = file1.getPath();
    	}

	    ibvcConfig_ = ibvcConfig;
		sfvcRevision_ = sfvcRevision;
		addiotinalArguments_ = addiotinalArguments;
		parameters_ = parameters;
    }
    
    @Override
    public BuildWrapper.Environment setUp(AbstractBuild build,
            Launcher launcher,
            BuildListener listener)
     throws IOException,
            InterruptedException{
    	
    	HashMap<String, String> envVars = new HashMap<String, String>();     

		envVars.put("IBVC_CONFIG", ibvcConfig_);
    	
    	if((sfvcRevision_ != null) && (sfvcRevision_.length() > 0)){
    		envVars.put("IBVC_TARGET_SFVC_REV", sfvcRevision_);
    	}
    	
    	if(parameters_ != null){
	    	for(IbvcParameter prm : parameters_){
	    		String k = String.format("IBVC_PARAM_%s", prm.getName());
    			envVars.put(k, prm.getValue());
	    	}
    	}
    	
    	if((bestMatchIbvcRev_ != null) && (bestMatchIbvcRev_.length() > 0)){
    		envVars.put("IBVC_BEST_MATCH_IBVC_REV", bestMatchIbvcRev_);
    	}
    	
    	if((bestMatchSfvcRev_ != null) && (bestMatchSfvcRev_.length() > 0)){
    		envVars.put("IBVC_BEST_MATCH_SFVC_REV", bestMatchSfvcRev_);
    	}

    	IbvcBuildWrapper.Environment env = new IbvcBuildWrapper.Environment(envVars);
    	env.buildEnvVars(envVars);
    	return env;    	
    }
	

	/**
	 * Launch IBVC with parameters
	 * IBVC out and err are streamed to console log
	 * Exit code is parsed to check IBVC result
	 * On successful exit, IBVC out is parsed to detect best-match IBVC and SFVC revisions.
	 * Revisions are kept to later be set in build environment variables.
	 *  
	 */
    @Override
    public void preCheckout(AbstractBuild build,
            Launcher launcher,
            BuildListener listener)
     throws IOException,
            InterruptedException
    {
	    final EnvVars vars = build.getEnvironment(listener);
    	
    	// Detect home, license file from node properties
		String ibvcPath = vars.expand(Util.nodeIbvcPath(build));
		listener.getLogger().println(String.format("%s: '%s'",Messages.IBVC_PATH(), ibvcPath));

		String ibvcLic = vars.expand(Util.nodeLicensePath(build));
	    listener.getLogger().println(String.format("%s: '%s'", Messages.IBVC_LICENSE(), ibvcLic));
	    
		ArrayList<String> args = new ArrayList<String>();
        ProcStarter ps = launcher.new ProcStarter();
        
        // Build command line
		args.add(ibvcPath);
	    args.add("--operation");
		args.add("checkout-best-match");
		
		if(ibvcConfig_.length() > 0){
		    args.add("--ibvc-config");
			args.add(vars.expand(ibvcConfig_));
		}
		
		if( ibvcLic.length() > 0){
			args.add("--lic-file");
			args.add(vars.expand(ibvcLic));
		}
			
		if( sfvcRevision_.length() > 0){
			args.add("--sfvc-revision");
			args.add(vars.expand(sfvcRevision_));
		}
		
		if( addiotinalArguments_.length() > 0){
			args.add(vars.expand(addiotinalArguments_));
		}

		if( parameters_ != null){
			for( IbvcParameter p : parameters_){
				args.add("--param-" + vars.expand( p.getName()));
				args.add(vars.expand(p.getValue()));
			}
		}

        ps.cmds(args);
        ps.stderr(listener.getLogger());
        ps.stdout(listener.getLogger());
        
        try {
        	Proc ibPrc = launcher.launch(ps);
        	int ibExitCode = ibPrc.join();

        	// Analyze exit code.
        	switch( ibExitCode){
        	case 0:
    	        listener.getLogger().println( Messages.IBVC_finished_successfully());
    	        break;
    	        
			default:
				build.setResult(Result.FAILURE);
				throw new AbortException(Messages.IBVC_terminated_with_errors());
        	}

		} catch (IOException e) {
	        listener.getLogger().println( e.getMessage());
			throw e;
		} catch (InterruptedException e) {
	        listener.getLogger().println( String.format( "%s: %s", Messages.IBVC_failed_to_finish_properly(), e.getMessage()));
			throw e;
		}
        
        // We get here after successful checkout. Parsing best-match
        Pattern regex = Pattern.compile(".*Checking out IBVC revision (?<IBVC>\\w+) and SFVC revision (?<SFVC>\\w+)$");
        for (Object line : build.getLog(10000)){
        	Matcher m = regex.matcher(line.toString());
        	if(m.matches()){
        		bestMatchIbvcRev_ = m.group("IBVC");
        		bestMatchSfvcRev_ = m.group("SFVC");

    	        listener.getLogger().println( String.format( 
    	        		"Will write IBVC_BEST_MATCH_SFVC_REV='%s' and IBVC_BEST_MATCH_IBVC_REV='%s' to build environment"
    	        		, bestMatchSfvcRev_
    	        		, bestMatchIbvcRev_));
        		break;
        	}
        }
	}
    
	public String getIbvcConfig(){
		return ibvcConfig_;
	}
	public String getSfvcRevision(){
		return sfvcRevision_;
	}
	public String getAddiotinalArguments(){
		return addiotinalArguments_;
	}
	public Collection<IbvcParameter> getParameters(){
		return parameters_;
	}
	
	public class Environment extends BuildWrapper.Environment {
		
		private final Map<String,String> envVars_;
		
		public Environment(Map<String,String> envVars){
			envVars_ = envVars;
		}
		
		@Override
		public void buildEnvVars(Map<String,String> env){
			for(Entry<String, String> k : envVars_.entrySet()){
				if (!env.containsKey(k.getKey())){
					env.put(k.getKey(), k.getValue());
				}
			}
		}
		
		@Override
		public boolean tearDown(AbstractBuild build, BuildListener listener) throws IOException, InterruptedException {
			return true;
		}
		
		@Override
		public boolean tearDown(Build build, BuildListener listener) throws IOException, InterruptedException{
			return true;
		}
    }

    @Extension
    public static final class DescriptorImpl extends BuildWrapperDescriptor {
        public DescriptorImpl() {
			super(IbvcBuildWrapper.class);
			load();
        }

        @Override
        public BuildWrapper newInstance(StaplerRequest req, JSONObject formData) throws FormException {
        	IbvcBuildWrapper wrp = (IbvcBuildWrapper) super.newInstance(req, formData);
            return wrp;
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            save();
            return true;
        }

        @Override
        public String getDisplayName() {
            return Messages.IBVC_buildWrapper();
        }

		@Override
		public boolean isApplicable(AbstractProject<?, ?> arg0) {
			return true;
		}
    }
}
