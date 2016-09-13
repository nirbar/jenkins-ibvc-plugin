package jenkins.plugins.Ibvc;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Map.Entry;
import java.util.Collection;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
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
import org.jenkinsci.Symbol;

public class IbvcPostBuildSave extends Recorder {

    private String ibvcConfig_;
    private boolean purgeOther_;
    private boolean keep_;
    private String addiotinalArguments_;
    private Collection<IbvcParameter> parameters_;
	private boolean overrideIbvcScmConfig_;
	
	public String getIbvcConfig(){
		return ibvcConfig_;
	}	
	@DataBoundSetter
	public void setIbvcConfig(String ibvcConfig){
		File file1 = new File(ibvcConfig);
		ibvcConfig = file1.getPath();
		ibvcConfig_ = ibvcConfig;
	}
	
	public boolean isPurgeOther(){
		return purgeOther_;
	}
	@DataBoundSetter
	public void setPurgeOther(boolean purgeOther){
		purgeOther_ = purgeOther;
	}
	
	public boolean isKeep(){
		return keep_;
	}
	@DataBoundSetter
	public void setKeep(boolean keep){
		keep_ = keep;
	}
	
	public String getAddiotinalArguments(){
		return addiotinalArguments_;
	}
	@DataBoundSetter
	public void setAddiotinalArguments(String args){
		addiotinalArguments_ = args;
	}

	public Collection<IbvcParameter> getParameters(){
		return parameters_;
	}
	@DataBoundSetter
	public void setParameters(Collection<IbvcParameter> args){
		if (overrideIbvcScmConfig_){
			parameters_ = args;
		}
	}

	public boolean isOverrideIbvcScmConfig(){
		return overrideIbvcScmConfig_;
	}
	@DataBoundSetter
	public void setOverrideIbvcScmConfig(boolean overrideIbvcScmConfig){
		overrideIbvcScmConfig_ = overrideIbvcScmConfig;
	}

	@Override
	public boolean perform(AbstractBuild<?,?> build, Launcher launcher, BuildListener listener)
              throws InterruptedException, IOException{
		
		if(build.getResult() != Result.SUCCESS){
	        listener.getLogger().println( Messages.Skipping_IBVC_Save_on_failure());
			return true;
		}
      
    	// Detect home, license file from node properties
		String ibvcPath = Util.nodeIbvcPath(build);
		String ibvcLic = Util.nodeLicensePath(build);

		ArrayList<String> args = new ArrayList<String>();
        ProcStarter ps = launcher.new ProcStarter();

        // Build command line
		args.add(ibvcPath);
		args.add("--operation");
		args.add("checkin");
		
		String ibvcConfig = ibvcConfig_;
		if(!overrideIbvcScmConfig_ || (ibvcConfig == null) || (ibvcConfig.length() == 0)){			
			EnvVars vars = build.getEnvironment(listener);
			if((vars != null) && vars.containsKey("IBVC_CONFIG")){
				ibvcConfig = vars.get("IBVC_CONFIG");
			}
		}
		if((ibvcConfig != null) && (ibvcConfig.length() > 0)){
		    args.add("--ibvc-config");
			args.add(ibvcConfig);
		} 		
		
		if(ibvcLic.length() > 0){
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
			args.add(addiotinalArguments_);
		}
		
		if(!overrideIbvcScmConfig_ || (parameters_ == null) || (parameters_.size() == 0)){
			EnvVars vars = build.getEnvironment(listener);
			if(vars != null){
				for (Entry<String, String> kv : vars.entrySet()){
				    if(kv.getKey().startsWith("IBVC_PARAM_")){
				    	String k = kv.getKey().substring("IBVC_PARAM_".length());
						args.add( "--param-" + k);
						args.add( kv.getValue());
				    }
				}
			}
		}
		else{
			for( IbvcParameter p : parameters_){
				if(p.getName().length() > 0){
					args.add( "--param-" + p.getName());
					args.add( p.getValue());
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
    @Symbol("ibvc_checkin")
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
