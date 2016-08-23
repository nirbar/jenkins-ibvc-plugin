package jenkins.plugins.Ibvc;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;
import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import net.sf.json.JSONObject;

public class IbvcParameter extends AbstractDescribableImpl<IbvcParameter> {
	
	private final String name_;
	private final String value_;
	
	@DataBoundConstructor
	public IbvcParameter( String name, String value){
		name_ = name;
		value_ = value;
	}
	
	public String getName(){
		return name_;
	}
	
	public String getValue(){
		return value_;
	}
    
	@Extension
    public static final class ParameterDescriptor extends Descriptor<IbvcParameter> {
        public ParameterDescriptor() {
			super(IbvcParameter.class);
			load();
        }

        @Override
        public IbvcParameter newInstance(StaplerRequest req, JSONObject formData) throws FormException {
        	IbvcParameter prm = (IbvcParameter) super.newInstance(req, formData);
            return prm;
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            save();
            return true;
        }

        @Override
        public String getDisplayName() {
            return Messages.parameter();
        }
    }
}
