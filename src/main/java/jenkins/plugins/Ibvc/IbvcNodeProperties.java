package jenkins.plugins.Ibvc;

import hudson.slaves.NodeProperty;
import hudson.slaves.NodePropertyDescriptor;
import hudson.model.Node;
import hudson.Extension;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

public class IbvcNodeProperties extends NodeProperty<Node>{

	private final String _home;
	private final String _license;

    @DataBoundConstructor
    public IbvcNodeProperties(String home, String license) {
		
		_home = home;
		_license = license;
    }
	
	public String getHome(){
		return _home;
	}
	
	public String getLicense(){
		return _license;
	}

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)super.getDescriptor();
    }		

    @Extension
    public static final class DescriptorImpl extends NodePropertyDescriptor {

        public DescriptorImpl() {
        }

        @Override
        public String getDisplayName() {
            return Messages.IBVC();
        }
        
        @Override
        public boolean isApplicableAsGlobal(){
        	return false;
        }
 
        @Override
        public IbvcNodeProperties newInstance(StaplerRequest req, JSONObject formData) throws FormException {
            IbvcNodeProperties p = (IbvcNodeProperties) super.newInstance(req, formData);
            return p;
        }
   }
}