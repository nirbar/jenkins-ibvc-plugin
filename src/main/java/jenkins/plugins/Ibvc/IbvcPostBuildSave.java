package jenkins.plugins.Ibvc;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Map.Entry;
import java.util.Collection;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import hudson.AbortException;
import hudson.EnvVars;
import hudson.Extension;
import hudson.Launcher;
import hudson.Launcher.ProcStarter;
import hudson.Proc;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Result;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import net.sf.json.JSONObject;

public class IbvcPostBuildSave extends Recorder {

    private final String ibvcConfig_;
    private final boolean purgeOther_;
    private final boolean keep_;
    private final String addiotinalArguments_;
    private final Collection<IbvcParameter> parameters_;
	private final boolean overrideIbvcScmConfig_;

    @DataBoundConstructor
    public IbvcPostBuildSave(
        String ibvcConfig
		, boolean keep
		, boolean purgeOther
		, String addiotinalArguments
		, Collection<IbvcParameter> parameters
		, boolean overrideIbvcScmConfig
		)
    {		
		// Override IBVC SCM defaults?
		overrideIbvcScmConfig_ = overrideIbvcScmConfig;		
		if(overrideIbvcScmConfig_){
			if((ibvcConfig != null) && (ibvcConfig.length() > 0)){
				File file1 = new File(ibvcConfig);
				ibvcConfig = file1.getPath();
				ibvcConfig_ = ibvcConfig;
			}
			else{
				ibvcConfig_ = null;				
			}
			
			if((parameters != null) && (parameters.size() > 0)){
				parameters_ = parameters;
			}
			else{
				parameters_ = null;
			}
		}
		else{
			ibvcConfig_ = null;
			parameters_ = null;
		}

		
		purgeOther_ = purgeOther;
		keep_ = keep;
		addiotinalArguments_ = addiotinalArguments;
    }
	
	public String getIbvcConfig(){
		return ibvcConfig_;
	}
	public boolean isPurgeOther(){
		return purgeOther_;
	}
	public boolean isKeep(){
		return keep_;
	}
	public String getAddiotinalArguments(){
		return addiotinalArguments_;
	}
	public Collection<IbvcParameter> getParameters(){
		return parameters_;
	}
	public boolean isOverrideIbvcScmConfig(){
		return overrideIbvcScmConfig_;
	}

	@Override
	public boolean perform(AbstractBuild<?,?> build, Launcher launcher, BuildListener listener)
              throws InterruptedException, IOException{
		
		if(build.getResult() != Result.SUCCESS){
	        listener.getLogger().println( Messages.Skipping_IBVC_Save_on_failure());
			return true;
		}
	    final EnvVars vars = build.getEnvironment(listener);
    	
    	// Detect home, license file from node properties
		String ibvcPath = vars.expand(Util.nodeIbvcPath(build));
		String ibvcLic = vars.expand(Util.nodeLicensePath(build));

		ArrayList<String> args = new ArrayList<String>();
        ProcStarter ps = launcher.new ProcStarter();

        // Build command line
		args.add(ibvcPath);
		args.add("--operation");
		args.add("checkin");
		
		String ibvcConfig = ibvcConfig_;
		if((ibvcConfig == null) || (ibvcConfig.length() == 0)){			
			if((vars != null) && vars.containsKey("IBVC_CONFIG")){
				ibvcConfig = vars.expand(vars.get("IBVC_CONFIG"));
			}
		}
		if((ibvcConfig != null) && (ibvcConfig.length() > 0)){
		    args.add("--ibvc-config");
			args.add(ibvcConfig);
		} 		
		
		if( ibvcLic.length() > 0){
			args.add("--lic-file");
			args.add(ibvcLic);
		}
		
		if (keep_){
			args.add("--keep");
		}
		
		if (purgeOther_){
			args.add("--purge-other");
		}
			
		if( addiotinalArguments_.length() > 0){
			args.add(vars.expand(addiotinalArguments_));
		}
		
		if((parameters_ == null) || (parameters_.size() == 0)){
			for (Entry<String, String> kv : vars.entrySet()){
			    if(kv.getKey().startsWith("IBVC_PARAM_")){
			    	String k = kv.getKey().substring("IBVC_PARAM_".length());
					args.add("--param-" + vars.expand(k));
					args.add(vars.expand(kv.getValue()));
			    }
			}
		}
		else{
			for( IbvcParameter p : parameters_){
				if(p.getName().length() > 0){
					args.add( "--param-" + vars.expand(p.getName()));
					args.add( vars.expand(p.getValue()));
				}
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
	    return true;
     }
	
	public BuildStepMonitor getRequiredMonitorService() {
		return BuildStepMonitor.NONE;
	}
	
	@Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }
    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {
        public DescriptorImpl() {
			super(IbvcPostBuildSave.class);
			load();
        }
        
        @Override
        public IbvcPostBuildSave newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            IbvcPostBuildSave scm = (IbvcPostBuildSave) super.newInstance(req, formData);
            return scm;
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            save();
            return true;
        }

        @Override
        public String getDisplayName() {
            return Messages.IBVC_save();
        }

		@Override
		public boolean isApplicable(Class<? extends AbstractProject> arg0) {
			return true;
		}
    }
}
