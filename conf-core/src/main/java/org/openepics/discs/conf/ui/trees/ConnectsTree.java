package org.openepics.discs.conf.ui.trees;

import java.util.ArrayList;
import java.util.List;

import org.openepics.discs.conf.ejb.SlotEJB;
import org.openepics.discs.conf.ent.Slot;
import org.openepics.discs.conf.ui.util.ConnectsManager;
import org.openepics.discs.conf.views.SlotView;

/**
 * Implements extrinsic method, that return's tree node's children based on connects database.
 * Takes care of removing cycles.
 * 
 * @author ilist
 *
 */
public class ConnectsTree extends Tree<SlotView> {
	private final ConnectsManager connectsManager;	
	 
	public ConnectsTree(SlotEJB slotEJB, ConnectsManager connectsManager) {
		super(slotEJB);						
		this.connectsManager = connectsManager;
	}
	
	
	@Override
	public List<? extends BasicTreeNode<SlotView>> getAllChildren(BasicTreeNode<SlotView> parent) {
		final SlotView parentSlotView = parent.getData();
		final Slot parentSlot = parentSlotView.getSlot();
		
		List<Slot> childSlots = connectsManager.getSlotConnects(parentSlot);
		  
		final List<BasicTreeNode<SlotView>> allChildren = new ArrayList<>();
		
		for (Slot child : childSlots) {
			if (hasCycle(parentSlotView, child.getId())) {
				// This sets the default for parent if the only connection from the slot is to itself.
                // If there are any other connections, this will get set to correct value after the loop.
				parentSlotView.setCableNumber(connectsManager.getCobles(parentSlot, child).get(0).getNumber());
				continue;
			}
			final SlotView childSlotView = new SlotView(child, parentSlotView, 0, slotEJB);
	        childSlotView.setLevel(parentSlotView.getLevel()+1);
	        allChildren.add(new FilteredTreeNode<SlotView>(childSlotView, parent, this));
	    	
	        childSlotView.setCableNumber(connectsManager.getCobles(parentSlot, child).get(0).getNumber()); // points to parent
	    }
		
		if (allChildren.size() > 0) {  // points to first child // TREE lazy loading may introduce bugs here
			parentSlotView.setCableNumber(connectsManager.getCobles(parentSlot, allChildren.get(0).getData().getSlot()).get(0).getNumber());
		}
		return allChildren;
	}


	private boolean hasCycle(SlotView parentSlotView, Long id) {
		while (parentSlotView != null) {
			if (id.equals(parentSlotView.getId())) return true;
			parentSlotView = parentSlotView.getParentNode();
		}
		return false;
	}
	
}
