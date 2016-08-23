package jenkins.plugins.Ibvc;

import hudson.AbortException;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.scm.ChangeLogParser;
import hudson.scm.SCM;
import hudson.scm.SCMDescriptor;
import hudson.scm.SCMRevisionState;
import hudson.Proc;
import hudson.Launcher.ProcStarter;
import java.io.File;
import java.io.IOException;
import java.lang.InterruptedException;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import java.util.ArrayList;
import java.util.Map;
import hudson.model.AbstractBuild;
import hudson.model.Result;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Collection;

public class IbvcSCMPlugin extends SCM {

    private final String ibvcConfig_;
    private final String sfvcRevision_;
    private final String addiotinalArguments_;
    private final Collection<IbvcParameter> parameters_;
    
    private String bestMatchSfvcRev_ = null;
    private String bestMatchIbvcRev_ = null;

    @DataBoundConstructor
    public IbvcSCMPlugin(
        String ibvcConfig
		, String sfvcRevision
		, String addiotinalArguments
		, Collection<IbvcParameter> parameters
		)
    {
    	if(ibvcConfig.length() > 0){
		    File file1 = new File(ibvcConfig);
		    ibvcConfig = file1.getPath();
    	}

	    ibvcConfig_ = ibvcConfig;
		sfvcRevision_ = sfvcRevision;
		addiotinalArguments_ = addiotinalArguments;
		parameters_ = parameters;
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
	
	/**
	 * Set environment variables for the build that can be used by subsequent steps
	 *  <p>
	 *  IBVC_CONFIG: Path of ibvc.config parameter
	 *  IBVC_TARGET_SFVC_REV: Requested SFVC target revision
	 *  IBVC_PARAM_name: IBVC custom parameter name and value
	 *  IBVC_BEST_MATCH_SFVC_REV: Detected best-match SFVC revision
	 *  IBVC_BEST_MATCH_IBVC_REV: Detected best-match IBVC revision
	 *
	 */
	@Override
	public void buildEnvVars(AbstractBuild<?,?> build,
            Map<String,String> env){
    	
    	if(!env.containsKey("IBVC_CONFIG")){
    		env.put("IBVC_CONFIG", ibvcConfig_);
    	}
    	
    	if((sfvcRevision_ != null) && (sfvcRevision_.length() > 0) && !env.containsKey("IBVC_TARGET_SFVC_REV")){
    		env.put("IBVC_TARGET_SFVC_REV", sfvcRevision_);
    	}
    	
    	if(parameters_ != null){
	    	for(IbvcParameter prm : parameters_){
	    		String k = String.format("IBVC_PARAM_%s", prm.getName());
	    		if(!env.containsKey(k)){
	    			env.put(k, prm.getValue());
	    		}
	    	}
    	}
    	
    	if((bestMatchIbvcRev_ != null) && (bestMatchIbvcRev_.length() > 0)){
    		env.put("IBVC_BEST_MATCH_IBVC_REV", bestMatchIbvcRev_);
    	}
    	
    	if((bestMatchSfvcRev_ != null) && (bestMatchSfvcRev_.length() > 0)){
    		env.put("IBVC_BEST_MATCH_SFVC_REV", bestMatchSfvcRev_);
    	}
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
    public void checkout(
        Run<?,?> build,
        Launcher launcher,
        FilePath workspace,
        TaskListener listener,
        File changelogFile,
        SCMRevisionState baseline
		) throws IOException, InterruptedException
    {
    	
    	// Detect home, license file from node properties
		String ibvcPath = Util.nodeIbvcPath(build);
		listener.getLogger().println(String.format("%s: '%s'",Messages.IBVC_PATH(), ibvcPath));

		String ibvcLic = Util.nodeLicensePath(build);
	    listener.getLogger().println(String.format("%s: '%s'", Messages.IBVC_LICENSE(), ibvcLic));

		ArrayList<String> args = new ArrayList<String>();
        ProcStarter ps = launcher.new ProcStarter();
        
        // Build command line
		args.add(ibvcPath);
		
		if(ibvcConfig_.length() > 0){
		    args.add("--ibvc-config");
			args.add(ibvcConfig_);
		}
		
		if( ibvcLic.length() > 0){
			args.add("--lic-file");
			args.add(ibvcLic);
		}
			
		if( sfvcRevision_.length() > 0){
			args.add("--sfvc-revision");
			args.add(sfvcRevision_);
		}
		
		if( addiotinalArguments_.length() > 0){
			args.add(addiotinalArguments_);
		}

		if( parameters_ != null){
			for( IbvcParameter p : parameters_){
				args.add( "--param-" + p.getName());
				args.add( p.getValue());
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
        for( String line : build.getLog(10000)){
        	Matcher m = regex.matcher(line);
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

    @Override
    public ChangeLogParser createChangeLogParser() {
		return null;
    }

    @Extension
    public static final class DescriptorImpl extends SCMDescriptor<IbvcSCMPlugin> {
        public DescriptorImpl() {
			super(IbvcSCMPlugin.class, null);
			load();
        }

        @Override
        public SCM newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            IbvcSCMPlugin scm = (IbvcSCMPlugin) super.newInstance(req, formData);
            return scm;
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            save();
            return true;
        }

        @Override
        public String getDisplayName() {
            return Messages.IBVC();
        }
    }
}
