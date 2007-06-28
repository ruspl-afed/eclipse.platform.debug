/*******************************************************************************
 * Copyright (c) 2007 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 * 	   Wind River - Pawel Piech - Initial Implementation - Drag/Drop to Expressions View (Bug 184057)
 *     IBM Corporation - further implementation and documentation
 *******************************************************************************/
package org.eclipse.debug.internal.ui.views.expression;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.IExpressionManager;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.model.IDebugElement;
import org.eclipse.debug.core.model.IExpression;
import org.eclipse.debug.core.model.IVariable;
import org.eclipse.debug.core.model.IWatchExpression;
import org.eclipse.debug.internal.core.ExpressionManager;
import org.eclipse.debug.internal.ui.DebugUIPlugin;
import org.eclipse.debug.internal.ui.viewers.model.provisional.TreeModelViewer;
import org.eclipse.debug.internal.ui.views.variables.IndexedVariablePartition;
import org.eclipse.debug.ui.DebugUITools;
import org.eclipse.debug.ui.actions.IWatchExpressionFactoryAdapter;
import org.eclipse.debug.ui.actions.IWatchExpressionFactoryAdapterExtension;
import org.eclipse.jface.util.LocalSelectionTransfer;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ViewerDropAdapter;
import org.eclipse.swt.dnd.DND;
import org.eclipse.swt.dnd.DropTargetEvent;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.TransferData;

/**
 * Drop Adapter allowing expressions, variables and text to be dropped in the Expression View.
 * When IVariables or text is dropped new watch expressions are created at the drop location.
 * When IExpressions are dropped, they are moved to the drop location
 * 
 * @see org.eclipse.debug.internal.ui.views.variables.VariablesDragAdapter
 * @see ExpressionManager
 * @since 3.4
 */
public class ExpressionDropAdapter extends ViewerDropAdapter {

	private TransferData fCurrentTransferType = null;
	private boolean fInsertBefore;
	private int fDropType;
	
	private static final int DROP_TYPE_DEFAULT = 0;
	private static final int DROP_TYPE_VARIABLE = 1;
	private static final int DROP_TYPE_EXPRESSION = 2;

    /**
     * Constructor takes the viewer this drop adapter applies to.
     * @param viewer the viewer to add drop to
     */
    protected ExpressionDropAdapter(TreeModelViewer viewer) {
        super(viewer);
        setFeedbackEnabled(true);
        setSelectionFeedbackEnabled(false);
        setScrollExpandEnabled(false);
    }

    /* (non-Javadoc)
     * @see org.eclipse.jface.viewers.ViewerDropAdapter#dragEnter(org.eclipse.swt.dnd.DropTargetEvent)
     */
    public void dragEnter(DropTargetEvent event) {
        super.dragEnter(event);

        for (int i = 0; i < event.dataTypes.length; i++) {
            if (LocalSelectionTransfer.getTransfer().isSupportedType(event.dataTypes[i])) {
            	if (isVariableDrop()){
                    event.currentDataType = event.dataTypes[i];
                    event.detail = DND.DROP_COPY;
                    fDropType = DROP_TYPE_VARIABLE;
                    return;
                } else if (isExpressionDrop()){
                	event.currentDataType = event.dataTypes[i];
                    event.detail = DND.DROP_MOVE;
                    fDropType = DROP_TYPE_EXPRESSION;
                    return;
                }
            }
        }

        for (int i = 0; i < event.dataTypes.length; i++) {
            if (TextTransfer.getInstance().isSupportedType(event.dataTypes[i])) {
                event.currentDataType = event.dataTypes[i];
                event.detail = DND.DROP_COPY;
                fDropType = DROP_TYPE_DEFAULT;
                return;
            }
        }

        fDropType = DROP_TYPE_DEFAULT;
        event.detail = DND.DROP_NONE;
    }
    
    /**
     * @return whether the selection transfer contains only IExpressions
     */
    private boolean isExpressionDrop() {
	    IStructuredSelection selection = (IStructuredSelection) LocalSelectionTransfer.getTransfer().getSelection();
	    Iterator iterator = selection.iterator();
	    while (iterator.hasNext()) {
	    	Object element = iterator.next();
	        if (!(element instanceof IExpression)){
	        	return false;
	        }
	    }
	    return true;
	}

	/**
	 * @return whether the selection transfer contains only IVariables
	 */
	private boolean isVariableDrop() {
	    IStructuredSelection selection = (IStructuredSelection) LocalSelectionTransfer.getTransfer().getSelection();
	    Iterator iterator = selection.iterator();
	    while (iterator.hasNext()) {
	    	Object element = iterator.next();
	        if (!(element instanceof IVariable)){
	        	return false;
	        }
	    }
	    return true;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jface.viewers.ViewerDropAdapter#dragOver(org.eclipse.swt.dnd.DropTargetEvent)
	 */
	public void dragOver(DropTargetEvent event) {
    	super.dragOver(event);
        // Allow scrolling (but not expansion)
    	event.feedback |= DND.FEEDBACK_SCROLL;
    }

    /* (non-Javadoc)
     * @see org.eclipse.jface.viewers.ViewerDropAdapter#validateDrop(java.lang.Object, int, org.eclipse.swt.dnd.TransferData)
     */
    public boolean validateDrop(Object target, int operation, TransferData transferType) {
        if (LocalSelectionTransfer.getTransfer().isSupportedType(transferType)) {
        	if (fDropType == DROP_TYPE_EXPRESSION){
        		return validateExpressionDrop(target);
        	} else if (fDropType == DROP_TYPE_VARIABLE){
        		return validateVariableDrop(target);
        	}
        } else if (TextTransfer.getInstance().isSupportedType(transferType)) {
            return true;
        }
        return false;
    }

	/**
	 * Validates if an IExpression drop is valid by checking if the target
	 * is an IExpression.
	 * @param target target of the drop
	 * @return whether the drop is valid
	 */
	private boolean validateExpressionDrop(Object target){
		return target instanceof IExpression;
	}

	/**
	 * Validates if the drop is valid by validating the local selection transfer 
	 * to ensure that a watch expression can be created for each contained IVariable.
	 * @param target target of the drop
	 * @return whether the drop is valid
	 */
	private boolean validateVariableDrop(Object target) {
		// Target must be null or an IExpression, you cannot add a new watch expression inside another
		if (target != null && !(target instanceof IExpression)){
			return false;
		}
	    IStructuredSelection selection = (IStructuredSelection) LocalSelectionTransfer.getTransfer().getSelection();
	    int enabled = 0;
	    int size = -1;
	    if (selection != null) {
	        size = selection.size();
	        IExpressionManager manager = DebugPlugin.getDefault().getExpressionManager();
	        Iterator iterator = selection.iterator();
	        while (iterator.hasNext()) {
	            Object element = iterator.next();
	            if (element instanceof IVariable){
	                IVariable variable = (IVariable) element;
	                if (variable instanceof IndexedVariablePartition) {
	                    break;
	                } else if (manager.hasWatchExpressionDelegate(variable.getModelIdentifier()) && isFactoryEnabled(variable)) {
	                    enabled++;
	                } else {
	                    break;
	                }
	            }
	        }
	    }
	    return enabled == size;
	}

	/**
	 * Returns whether the factory adapter for the given variable is currently enabled.
	 * 
	 * @param variable the variable to ask for the adapter
	 * @return whether the factory is enabled
	 */
	private boolean isFactoryEnabled(IVariable variable) {
	    IWatchExpressionFactoryAdapter factory = getFactory(variable);
	    if (factory instanceof IWatchExpressionFactoryAdapterExtension) {
	        IWatchExpressionFactoryAdapterExtension ext = (IWatchExpressionFactoryAdapterExtension) factory;
	        return ext.canCreateWatchExpression(variable);
	    }
	    return false;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jface.viewers.ViewerDropAdapter#drop(org.eclipse.swt.dnd.DropTargetEvent)
	 */
	public void drop(DropTargetEvent event) {
	    fCurrentTransferType = event.currentDataType;
	    // Unless insert after is explicitly set, insert before
	    fInsertBefore = getCurrentLocation() != LOCATION_AFTER;
	    super.drop(event);
	}

	/* (non-Javadoc)
     * @see org.eclipse.jface.viewers.ViewerDropAdapter#performDrop(java.lang.Object)
     */
    public boolean performDrop(Object data) {
        if (LocalSelectionTransfer.getTransfer().isSupportedType(fCurrentTransferType)) {
            IStructuredSelection selection = (IStructuredSelection) LocalSelectionTransfer.getTransfer().getSelection();
            if (fDropType == DROP_TYPE_EXPRESSION){
            	return performExpressionDrop(selection);
            } else if (fDropType == DROP_TYPE_VARIABLE){
            	return performVariableDrop(selection);
            }
        } else if (TextTransfer.getInstance().isSupportedType(fCurrentTransferType)) {
            if (data != null) {
            	return performTextDrop((String)data);
            }
        }
        return false;
    }

    /**
     * Performs the drop when the selection is a collection of IExpressions.
     * Moves the given expressions from their original locations to the
     * location of the current target.
     * @param selection the dragged selection
     * @return whether the drop could be completed
     */
    private boolean performExpressionDrop(IStructuredSelection selection) {
		if (getCurrentTarget() instanceof IExpression){
			IExpression[] expressions = new IExpression[selection.size()];
	    	System.arraycopy(selection.toArray(), 0, expressions, 0, expressions.length);
	    	
	    	IExpressionManager manager = DebugPlugin.getDefault().getExpressionManager();
	    	if (manager instanceof ExpressionManager){
	    		((ExpressionManager)manager).moveExpressions(expressions, (IExpression)getCurrentTarget(), fInsertBefore);
	    	}
	    	return true;
		}
		return false;
		
	}

	/**
     * If the dragged data is a structured selection, get any IVariables in it 
     * and create expressions for each of them.  Insert the created expressions
     * at the currently selected target or add them to the end of the collection
     * if no target is selected.
     * 
     * @param selection Structured selection containing IVariables
     * @return whether the drop was successful
     */
    private boolean performVariableDrop(IStructuredSelection selection) {
        List expressions = new ArrayList(selection.size());
    	for (Iterator itr = selection.iterator(); itr.hasNext(); ) {
            Object element = itr.next();
            if (element instanceof IVariable) {
            	String expressionText = createExpressionString((IVariable)element);
            	if (expressionText != null){
	            	IExpression expression = createExpression(expressionText);
	            	if (expression != null){
	            		expressions.add(expression);
	            	} else {
	            		DebugUIPlugin.log(new Status(IStatus.ERROR,DebugUIPlugin.getUniqueIdentifier(),"Drop failed.  Watch expression could not be created for the text " + expressionText)); //$NON-NLS-1$
	            		return false;
	            	}
            	} else {
            		return false;
            	}
            }
        }
    	if (expressions.size() == selection.size()){
    		IExpressionManager manager = DebugPlugin.getDefault().getExpressionManager();
	    	if (manager instanceof ExpressionManager){
	    		if (getCurrentTarget() != null){
	    			((ExpressionManager)manager).insertExpressions((IExpression[])expressions.toArray(new IExpression[expressions.size()]), (IExpression)getCurrentTarget(), fInsertBefore);
	    		} else {
	    			((ExpressionManager)manager).addExpressions((IExpression[])expressions.toArray(new IExpression[expressions.size()]));
	    		}
	    		return true;
	    	}
    	}
    	return false;
    }
    
    /**
     * Performs the drop when text was dragged.  Creates a new watch expression from
     * the text.  Inserts the expression at the currently selected target or adds it
     * to the end of the collection if no target is selected.
     * 
     * @param text string to use to create the expression
     * @return whether the drop was successful
     */
    private boolean performTextDrop(String text){
    	IExpression expression = createExpression(text);
    	if (expression != null){
    		IExpressionManager manager = DebugPlugin.getDefault().getExpressionManager();
	    	if (manager instanceof ExpressionManager){
		    	if (getCurrentTarget() != null){
	    			((ExpressionManager)manager).insertExpressions(new IExpression[]{expression}, (IExpression)getCurrentTarget(), fInsertBefore);
	    		} else {
	    			((ExpressionManager)manager).addExpression(expression);
	    		}
	    		return true;
	    	}
    	}
    	DebugUIPlugin.log(new Status(IStatus.ERROR,DebugUIPlugin.getUniqueIdentifier(),"Drop failed.  Watch expression could not be created for the text " + text)); //$NON-NLS-1$
    	return false;
    }
    
    /**
     * Creates a new watch expression from an IVariable using the watch expression factory
     * adapter for that variable.
     * 
     * @param variable the variable to use to create the watch expression
     * @return whether the creation was successful
     */
    private String createExpressionString(IVariable variable) {
        IWatchExpressionFactoryAdapter factory = getFactory(variable);
        try {
            String exp = variable.getName();
            if (factory != null) {
                exp = factory.createWatchExpression(variable);
                return exp;
            } else {
            	DebugUIPlugin.log(new Status(IStatus.ERROR,DebugUIPlugin.getUniqueIdentifier(),"Drop failed.  Watch Expression Factory could not be found for variable " + variable)); //$NON-NLS-1$
            }
        } catch (CoreException e) {
        	DebugUIPlugin.log(e.getStatus());
        }
        return null;
    }

    /**
     * Creates a new watch expression from a string using the default expression manager.
     * 
     * @param exp the string to use to create the expression
     */
    private IExpression createExpression(String exp) {
        IWatchExpression expression = DebugPlugin.getDefault().getExpressionManager().newWatchExpression(exp);
        IAdaptable object = DebugUITools.getDebugContext();
        IDebugElement context = null;
        if (object instanceof IDebugElement) {
            context = (IDebugElement) object;
        } else if (object instanceof ILaunch) {
            context = ((ILaunch) object).getDebugTarget();
        }
        expression.setExpressionContext(context);
        return expression;
    }
    
   
    /**
     * Returns the factory adapter for the given variable or <code>null</code> if none.
     * 
     * @param variable
     * @return factory or <code>null</code>
     */
    private IWatchExpressionFactoryAdapter getFactory(IVariable variable) {
        return (IWatchExpressionFactoryAdapter) variable.getAdapter(IWatchExpressionFactoryAdapter.class);      
    }

}
