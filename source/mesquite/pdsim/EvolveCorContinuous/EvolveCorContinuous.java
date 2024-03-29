/* Mesquite source code.  Copyright 1997-2007 W. Maddison and D. Maddison. 
Version 2.01, December 2007.
Disclaimer:  The Mesquite source code is lengthy and we are few.  There are no doubt inefficiencies and goofs in this code. 
The commenting leaves much to be desired. Please approach this source code with the spirit of helping out.
Perhaps with your help we can be more than a few, and make Mesquite better.

Mesquite is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY.
Mesquite's web site is http://mesquiteproject.org

This source code and its compiled class files are free and modifiable under the terms of 
GNU Lesser General Public License.  (http://www.gnu.org/copyleft/lesser.html)
 */
package mesquite.pdsim.EvolveCorContinuous;
/*~~  */

//import java.util.*;
//import java.awt.*;
import mesquite.lib.*;
import mesquite.lib.characters.*;
import mesquite.lib.duties.*;
import mesquite.cont.lib.*;
import mesquite.stochchar.lib.*;
import mesquite.pdsim.lib.*;


/* ======================================================================== */
public class EvolveCorContinuous extends CharacterSimulator implements MesquiteListener {
	ContinuousHistory evolvingStates;
	ProbabilityCorContCharModel probabilityModel, originalProbabilityModel;
	MesquiteString modelName;
	/*.................................................................................................................*/

	public boolean startJob(String arguments, Object condition, boolean hiredByName) {
		if (condition!=null && condition!= ContinuousData.class && condition!=ContinuousState.class){
			return sorry("Evolve Continuous cannot be used because it supplies only continuous-valued characters");
		}
		ProbabilityCorContCharModel m = chooseModel();
		if (m==null) {
			return sorry("Evolve Continuous Characters could not start because no appropriate stochastic model of character evolution was found for use to simulate continuous character evolution");
		}
		setModel(m,  false); 

		MesquiteSubmenuSpec mss = addSubmenu(null, "Model (for simulation)", makeCommand("setModelByNumber", this), getProject().getCharacterModels());
		mss.setCompatibilityCheck(new SimModelCompatInfo(ProbabilityCorContCharModel.class, null));
		modelName = new MesquiteString(m.getName());
		mss.setSelected(modelName);
		getProject().getCentralModelListener().addListener(this);
		return true;
	}
	
	public MesquiteString getmodelName(){
		return modelName;
	}

	private ProbabilityCorContCharModel chooseModel(){
		ProbabilityCorContCharModel m = null;
		if (MesquiteThread.isScripting())
			return (ProbabilityCorContCharModel)getProject().getCharacterModel(new SimModelCompatInfo(ProbabilityCorContCharModel.class, ContinuousState.class),0);
		else
			return (ProbabilityCorContCharModel)CharacterModel.chooseExistingCharacterModel(this, new SimModelCompatInfo(ProbabilityCorContCharModel.class, ContinuousState.class), "Choose probability model for simulations of continuous character evolution");
	}
	/*.................................................................................................................*/
	public void endJob() {
		getProject().getCentralModelListener().removeListener(this);
		if (probabilityModel !=null)
			probabilityModel.dispose();
		super.endJob();
	}
	private void setModel(ProbabilityCorContCharModel m,  boolean notify){
		originalProbabilityModel = m;
		if (probabilityModel !=null)
			probabilityModel.dispose();
		if (m!=null)
			probabilityModel = (ProbabilityCorContCharModel)m.cloneModelWithMotherLink(probabilityModel); //use a copy in case fiddled with
		if (notify)
			parametersChanged();
	}
	public void changed(Object caller, Object obj, Notification notification){
		if (obj == originalProbabilityModel) {
			setModel((ProbabilityCorContCharModel)obj,  true);
		}
	}
	/*.................................................................................................................*/
	/** passes which object is being disposed (from MesquiteListener interface)*/
	public void disposing(Object obj){
		if (obj == originalProbabilityModel) {
			setModel(chooseModel(), true);
		}
	}
	/*.................................................................................................................*/
	public Snapshot getSnapshot(MesquiteFile file) {
		Snapshot temp = new Snapshot();
		temp.addLine("setModelByName " + StringUtil.tokenize(probabilityModel.getName()));
		return temp;
	}
	/*.................................................................................................................*/
	public void employeeParametersChanged(MesquiteModule employee, MesquiteModule source, Notification notification) {
		super.employeeParametersChanged(employee, source, notification);
	}
	MesquiteInteger pos = new MesquiteInteger(0);
	/*.................................................................................................................*/
	public Object doCommand(String commandName, String arguments, CommandChecker checker) {
		if (checker.compare(this.getClass(), "Sets character model for simulation", "[name of model]", commandName, "setModelByName")) {
			pos.setValue(0);
			CharacterModel m = getProject().getCharacterModel(parser.getFirstToken(arguments));
			if (m!=null && m instanceof ProbabilityCorContCharModel) {
				setModel((ProbabilityCorContCharModel)m,  true);
				modelName.setValue(m.getName());
				return m;
			}
		}
		else if (checker.compare(this.getClass(), "Sets character model for simulation", "[number of model]", commandName, "setModelByNumber")) {
			pos.setValue(0);
			int whichModel = MesquiteInteger.fromFirstToken(arguments, pos);
			if (!MesquiteInteger.isCombinable(whichModel))
				return null;
			CharacterModel m = getProject().getCharacterModel(new ModelCompatibilityInfo(ProbabilityCorContCharModel.class, null), whichModel);
			if (m!=null && m instanceof ProbabilityCorContCharModel) {
				setModel((ProbabilityCorContCharModel)m,  true);
				modelName.setValue(m.getName());
				return m;
			}
		}
		else
			return  super.doCommand(commandName, arguments, checker);
		return null;
	}
	/*.................................................................................................................*/
	private void evolve (Tree tree, ContinuousAdjustable statesAtTips, int node) {
		if (node!=tree.getRoot()) {
			double stateAtAncestor = evolvingStates.getState(tree.motherOfNode(node));
			double stateAtNode = probabilityModel.evolveState(stateAtAncestor, tree, node);
			evolvingStates.setState(node, stateAtNode);  
			if (tree.nodeIsTerminal(node)) {
				statesAtTips.setState(tree.taxonNumberOfNode(node), stateAtNode);
			}
		}
		for (int daughter = tree.firstDaughterOfNode(node); tree.nodeExists(daughter); daughter = tree.nextSisterOfNode(daughter))
			evolve(tree, statesAtTips, daughter);
	}
	/*.................................................................................................................*/
	private void evolve (Tree tree, ContinuousHistory statesAtNodes, int node) {
		if (node!=tree.getRoot()) {
			double stateAtAncestor = statesAtNodes.getState(tree.motherOfNode(node));
			double stateAtNode = probabilityModel.evolveState(stateAtAncestor, tree, node);
			statesAtNodes.setState(node, stateAtNode);
		}
		for (int daughter = tree.firstDaughterOfNode(node); tree.nodeExists(daughter); daughter = tree.nextSisterOfNode(daughter))
			evolve(tree, (ContinuousHistory)statesAtNodes, daughter);
	}
	Tree oldTree=null;
	boolean failed = false;
	int warnedNoModel = 0;
	/*.................................................................................................................*/
	public CharacterDistribution getSimulatedCharacter(CharacterDistribution statesAtTips, Tree tree, MesquiteLong seed){
		if (tree == null) {
			//System.out.println("tree null in Evolve continuous chars ");
			return null;
		}
		else if (statesAtTips == null || !(statesAtTips instanceof ContinuousAdjustable)){
			statesAtTips =  new ContinuousAdjustable(tree.getTaxa(), tree.getTaxa().getNumTaxa());
		}
		else	
			statesAtTips = (CharacterDistribution)((AdjustableDistribution)statesAtTips).adjustSize(tree.getTaxa());
		if (statesAtTips instanceof ContinuousAdjustable)
			((ContinuousAdjustable)statesAtTips).deassignStates();

		if (probabilityModel == null){
			if (warnedNoModel++<10) {
				discreetAlert( "Sorry, the simulation cannot continue because no model of character evolution has been specified.");
				if (!MesquiteThread.isScripting())
					warnedNoModel = 10;
			}
			return statesAtTips;
		}	
		probabilityModel.setCharacterDistribution(statesAtTips);
		if (!probabilityModel.isFullySpecified()) {
			if (failed) {
				return statesAtTips;
			}	
			CharModelCurator mb = findModelCurator(probabilityModel.getClass());
			mb.showEditor(probabilityModel); //MODAL
			if (!probabilityModel.isFullySpecified()) {
				alert("Sorry, the simulation cannot continue because the model of character evolution ("  + probabilityModel.getName() + ") doesn't have all of its parameters specified.");
				failed = true;
				return statesAtTips;
			}
		}
		failed = false;
		evolvingStates =(ContinuousHistory) statesAtTips.adjustHistorySize(tree, evolvingStates);
		if (seed!=null)
			probabilityModel.setSeed(seed.getValue());
		evolvingStates.setState(tree.getRoot(), probabilityModel.getRootState(tree));  //starting root at ancestral state
		evolve(tree, (ContinuousAdjustable)statesAtTips, tree.getRoot());
		if (seed!=null)
			seed.setValue(probabilityModel.getSeed());
		oldTree = tree;
		return statesAtTips;
	}
	/*.................................................................................................................*/
	public CharacterHistory getSimulatedHistory(CharacterHistory statesAtNodes, Tree tree, MesquiteLong seed){
		if (tree == null) {
			//System.out.println("tree null in Evolve continuous chars ");
			return null;
		}
		else if (statesAtNodes == null || !(statesAtNodes instanceof ContinuousHistory)){
			statesAtNodes =  new ContinuousHistory(tree.getTaxa(), tree.getNumNodeSpaces(), null);
		}
		else	
			statesAtNodes = statesAtNodes.adjustSize(tree);

		if (probabilityModel == null){
			if (statesAtNodes instanceof ContinuousAdjustable)
				((ContinuousAdjustable)statesAtNodes).deassignStates();
			if (warnedNoModel++<10) {
				discreetAlert( "Sorry, the simulation cannot continue because no model of character evolution has been specified.");
				if (!MesquiteThread.isScripting())
					warnedNoModel = 10;
			}
			return statesAtNodes;
		}	
		probabilityModel.setCharacterDistribution(statesAtNodes);
		if (seed!=null)
			probabilityModel.setSeed(seed.getValue());
		if (!probabilityModel.isFullySpecified()) {
			if (failed) {
				statesAtNodes.deassignStates();
				return statesAtNodes;
			}	
			CharModelCurator mb = findModelCurator(probabilityModel.getClass());
			mb.showEditor(probabilityModel); //MODAL
			if (!probabilityModel.isFullySpecified()) {
				alert("Sorry, the simulation cannot continue because the model of character evolution ("  + probabilityModel.getName() + ") doesn't have all of its parameters specified.");
				statesAtNodes.deassignStates();
				failed = true;
				return statesAtNodes;
			}
		}
		failed = false;
		((ContinuousHistory)statesAtNodes).setState(tree.getRoot(), probabilityModel.getRootState(tree));  //starting root at ancestral state
		evolve(tree, (ContinuousHistory)statesAtNodes, tree.getRoot());
		if (seed!=null)
			seed.setValue(probabilityModel.getSeed());
		oldTree = tree;
		return statesAtNodes;
	}
	
	/*public ContinuousHistory[] getCorSimulatedHistory(ContinuousHistory[] states, Tree tree, CorTrait rng, EvolveCorContinuous[] models, MesquiteLong seed){
		int num_of_traits=rng.getTraitNum();
		if (tree == null) {
			System.out.println("tree null in Evolve corelated continuous chars ");
			return null;
		}
		else if (states == null || !(states instanceof ContinuousHistory[])){
			for (int x=0;x<num_of_traits;x++)
					states[x] =  new ContinuousHistory(tree.getTaxa(), tree.getNumNodeSpaces(), null);
		}
		else	
			for (int x=0;x<num_of_traits;x++)
					states[x] = (ContinuousHistory)states[x].adjustSize(tree);
		int local_warning=0;
		for (int x=0;x<num_of_traits;x++){
				if (models[x] == null){
				if (states[x] instanceof ContinuousAdjustable)
					((ContinuousAdjustable)states[x]).deassignStates();
				if (local_warning++<10) {
				//	discreetAlert( "Sorry, the simulation cannot continue because no model of character evolution has been specified.");
					if (!MesquiteThread.isScripting())
						local_warning = 10;
				}
				return states;
			}
		}
		for (int x=0;x<num_of_traits;x++){
			models[x].probabilityModel.setCharacterDistribution(states[x]);
			if (!models[x].probabilityModel.isFullySpecified()) {
			/*	if (failed) {
					statesAtNodes.deassignStates();
					return statesAtNodes;
				}*/	
				//CharModelCurator mb = findModelCurator(models[x].probabilityModel.getClass());
				//mb.showEditor(probabilityModel); //MODAL
					//if (!probabilityModel.isFullySpecified()) {
					//alert("Sorry, the simulation cannot continue because the model of character evolution ("  + models[x].probabilityModel.getName() + ") doesn't have all of its parameters specified.");
					//statesAtNodes.deassignStates();
					//failed = true;
					//return states;
					//}
			//}
			//failed = false;
			/*((ContinuousHistory)states[x]).setState(tree.getRoot(), models[x].probabilityModel.getRootState(tree));  //starting root at ancestral state
		}
		evolveMultiState(tree, (ContinuousHistory)states, tree.getRoot());
		if (seed!=null)
			seed.setValue(probabilityModel.getSeed());
		oldTree = tree;
		return states;
	}*/
	/*.................................................................................................................*/
	/** Indicates the type of character created */ 
	public Class getStateClass(){
		return ContinuousState.class;
	}
	/*.................................................................................................................*/
	public String getName() {
		return "Evolve Corelated Continuous Characters";
	}
	/*.................................................................................................................*/
	/** returns whether this module is requ		temp_charvector[b]=CorTraitTask[ModelVector[b]].evolveMultiState(charvector, model.traits, tree, start_node, b, false);
	esting to appear as a primary choice */
	public boolean requestPrimaryChoice(){
		return true;  
	}

	/*.................................................................................................................*/
	public String getParameters() {
		return "Markovian evolution using model: " + probabilityModel.getName() + " (" + probabilityModel.getParameters() + ")";
	}
	/*.................................................................................................................*/
	public boolean showCitation() {
		return true;
	}
	/*.................................................................................................................*/
	public boolean isPrerelease() {
		return false;
	}
	/*.................................................................................................................*/

	/** returns an explanation of what the module does.*/
	public String getExplanation() {
		return "Simulates character evolution for continuous characters on a tree." ;
	}
	public double[] evolveMultiState(double[] beginState, CorTrait model, Tree tree, int node, int trait_num, boolean bounding){
	//	temp_charvector[b]=CorTraitTask[ModelVector[b]].evolveMultiState(charvector, model.traits, tree, start_node, b, false);
		return probabilityModel.evolveMultiState(beginState, model, tree.getBranchLength(node), node, trait_num, bounding);
	}
	/*.................................................................................................................*/
	public CompatibilityTest getCompatibilityTest() {
		return new ECoCCategoricalStateTest();
	}
	public boolean isBound(){
		return probabilityModel.isBound();
	}
}
class ECoCCategoricalStateTest extends ContinuousStateTest{
	ModelCompatibilityInfo mci;
	public ECoCCategoricalStateTest (){
		super();
		mci = new ModelCompatibilityInfo(ProbabilityCorContCharModel.class, ContinuousState.class);
	}
	public  boolean isCompatible(Object obj, MesquiteProject project, EmployerEmployee prospectiveEmployer){
		if (project==null){
			return super.isCompatible(obj, project, prospectiveEmployer);
		}
		Listable[] models = project.getCharacterModels(mci, ContinuousState.class);
		if (models == null || models.length == 0)
			return false;
		boolean oneFound = false;
		for (int i=0; i<models.length; i++)
			if (((ProbabilityCorContCharModel)models[i]).isFullySpecified()) {
				oneFound = true;
				break;
			}
		if (oneFound){
			return super.isCompatible(obj, project, prospectiveEmployer);
		}
		return false;
	}

}