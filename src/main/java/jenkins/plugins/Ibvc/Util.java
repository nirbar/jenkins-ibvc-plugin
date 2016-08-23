package jenkins.plugins.Ibvc;

import java.io.File;

import hudson.model.AbstractBuild;
import hudson.model.Node;
import hudson.model.Run;
import hudson.slaves.NodeProperty;

public class Util {

	public static String nodeIbvcPath(Run<?,?> build)
	{
		String home = "IBVC";
		
		AbstractBuild<?,?> builder = (AbstractBuild<?,?>)build;
		if(builder == null){
			return home;
		}
		
		Node node = builder.getBuiltOn();
		if(node != null){
			for( NodeProperty<?> np : node.getNodeProperties()){
				if( np instanceof IbvcNodeProperties){					
					IbvcNodeProperties myNp = (IbvcNodeProperties)np;
					
					String tmp = myNp.getHome();
					if(tmp.length() > 0){
					    File file1 = new File(tmp);
					    File file2 = new File(file1, home);
					    home = file2.getPath();
					}
					break;
				}
			}
		}
		
		return home;
	}

	public static String nodeLicensePath(Run<?,?> build)
	{
		String lic = "";
		
		AbstractBuild<?,?> builder = (AbstractBuild<?,?>)build;
		if(builder == null){
			return lic;
		}
		
		Node node = builder.getBuiltOn();
		if(node != null){
			for( NodeProperty<?> np : node.getNodeProperties()){
				if( np instanceof IbvcNodeProperties){					
					IbvcNodeProperties myNp = (IbvcNodeProperties)np;
					
					String tmp = myNp.getLicense();			
					if( tmp.length() > 0){
					    File file1 = new File(tmp);
					    lic = file1.getPath();
					}
					break;
				}
			}
		}
		
		return lic;
	}

}
