/*******************************************************************************
 * Copyright (c) 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.debug.internal.ui.actions.breakpointGroups;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import org.eclipse.debug.internal.ui.SWTUtil;
import org.eclipse.debug.internal.ui.views.breakpoints.BreakpointContainerFactoryManager;
import org.eclipse.debug.internal.ui.views.breakpoints.BreakpointsView;
import org.eclipse.debug.ui.IBreakpointOrganizer;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.DoubleClickEvent;
import org.eclipse.jface.viewers.IDoubleClickListener;
import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.ui.internal.dialogs.ViewLabelProvider;

/**
 * Dialog which presents available breakpoint groupings to
 * the user and allows them to specify which they'd like
 * to use and in what order they should be applied.
 */
public class GroupBreakpointsByDialog extends Dialog {
	
	private BreakpointsView fView;
	
	// Table viewer that presents available containers
	private TableViewer fAvailableViewer;
	private AvailableOrganizersProvider fAvailableOrganizersProvider= new AvailableOrganizersProvider();
	
	// Tree viewer that presents selected containers
	private TreeViewer fSelectedViewer;
	private SelectedOrganizerProvider fSelectedOrganizersProvider= new SelectedOrganizerProvider();
	
	private List fResult= new ArrayList();

	private Button fAddButton;
	private Button fRemoveButton;
	private Button fMoveUpButton;
	private Button fMoveDownButton;
	
	/**
	 * Selection listener that listens to selection from all buttons in this
	 * dialog.
	 */
	private SelectionAdapter fSelectionListener= new SelectionAdapter() {
		public void widgetSelected(SelectionEvent e) {
			Object source= e.getSource();
			if (source == fAddButton) {
				handleAddPressed();
			} else if (source == fRemoveButton) {
				handleRemovePressed();
			} else if (source == fMoveUpButton) {
				handleMoveUpPressed();
			} else if (source == fMoveDownButton) {
				handleMoveDownPressed();
			}
		}
	};

	protected GroupBreakpointsByDialog(BreakpointsView view) {
		super(view.getSite().getShell());
		fView= view;
		setShellStyle(getShellStyle() | SWT.RESIZE);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jface.dialogs.Dialog#createDialogArea(org.eclipse.swt.widgets.Composite)
	 */
	protected Control createDialogArea(Composite parent) {
		ILabelProvider labelProvider= new BreakpointOrganzierLabelProvider();
		
		Composite parentComposite= (Composite) super.createDialogArea(parent);
		parentComposite.setFont(parent.getFont());
		Composite composite= new Composite(parentComposite, SWT.NONE);
		GridLayout layout = new GridLayout();
		layout.numColumns = 3;
        composite.setLayout(layout);
		GridData data= new GridData(GridData.FILL_BOTH);
		data.heightHint= 400;
		composite.setLayoutData(data);
		composite.setFont(parent.getFont());
		
		Label label= new Label(composite, SWT.WRAP);
		label.setText(BreakpointGroupMessages.getString("GroupBreakpointsByDialog.0")); //$NON-NLS-1$
		GridData gridData = new GridData(GridData.FILL_HORIZONTAL);
		gridData.horizontalSpan = 3;
        label.setLayoutData(gridData);
		
		createAvailableViewer(composite, labelProvider);
		createButtons(composite);
		createSelectedViewer(composite, labelProvider);

		initializeContent();
		updateViewers();
		Dialog.applyDialogFont(parentComposite);
		return parentComposite;
	}
	
	/**
	 * Divides the available breakpoint container factories into the
	 * appropriate viewers ("available" or "selected").
	 */
	private void initializeContent() {
		IBreakpointOrganizer[] organizers= BreakpointContainerFactoryManager.getDefault().getOrganizers();
		for (int i = 0; i < organizers.length; i++) {
			fAvailableOrganizersProvider.addAvailable(organizers[i]);
		}
		organizers = fView.getBreakpointOrganizers();
        if (organizers != null) {
    		for (int i = 0; i < organizers.length; i++) {
                fSelectedOrganizersProvider.addSelected(organizers[i]);
            }
        }
	}

	/**
	 * Creates and configured the viewer that shows the available (not currently selected)
	 * breakpoint container factories.
	 */
	private void createAvailableViewer(Composite parent, ILabelProvider labelProvider) {		
		Composite availableComposite= new Composite(parent, SWT.NONE);
		availableComposite.setFont(parent.getFont());
		GridLayout layout = new GridLayout();
		layout.marginHeight=0;
		layout.marginWidth=0;
		availableComposite.setLayout(layout);
		GridData gridData= new GridData(GridData.FILL_BOTH);
		gridData.widthHint= 225;
		availableComposite.setLayoutData(gridData);

		Label label= new Label(availableComposite, SWT.WRAP);
		label.setText(BreakpointGroupMessages.getString("GroupBreakpointsByDialog.1")); //$NON-NLS-1$
		gridData = new GridData(GridData.FILL_HORIZONTAL);
        label.setLayoutData(gridData);
		
		fAvailableViewer= new TableViewer(availableComposite);
		fAvailableViewer.setContentProvider(fAvailableOrganizersProvider);
		fAvailableViewer.setLabelProvider(labelProvider);
		fAvailableViewer.setInput(new Object());
		Table table = fAvailableViewer.getTable();
		table.setLayoutData(new GridData(GridData.FILL_BOTH));
		table.setFont(parent.getFont());
		fAvailableViewer.addDoubleClickListener(new IDoubleClickListener() {
			public void doubleClick(DoubleClickEvent event) {
				handleAddPressed();
			}
		});
		fAvailableViewer.addSelectionChangedListener(new ISelectionChangedListener() {
			public void selectionChanged(SelectionChangedEvent event) {
				updateAddButton();
			}
		});
	}

	/**
	 * Creates and configures the viewer that shows the currently selected
	 * breakpoint container factories.
	 */
	private void createSelectedViewer(Composite parent, ILabelProvider labelProvider) {
		Composite selectedComposite= new Composite(parent, SWT.NONE);
		GridLayout layout = new GridLayout();
		layout.marginHeight=0;
		layout.marginWidth=0;
		layout.numColumns= 2;
		selectedComposite.setLayout(layout);
		GridData gridData= new GridData(GridData.FILL_BOTH);
		gridData.widthHint= 225;
		selectedComposite.setLayoutData(gridData);
		selectedComposite.setFont(parent.getFont());
		
		Label label= new Label(selectedComposite, SWT.WRAP);
		label.setText(BreakpointGroupMessages.getString("GroupBreakpointsByDialog.3")); //$NON-NLS-1$
		gridData = new GridData();
		gridData.horizontalSpan = 2;
        label.setLayoutData(gridData);
		
		fSelectedViewer= new TreeViewer(selectedComposite);
		fSelectedViewer.setContentProvider(fSelectedOrganizersProvider);
		fSelectedViewer.setLabelProvider(labelProvider);
		fSelectedViewer.setInput(new Object());
		Tree tree = fSelectedViewer.getTree();
		tree.setLayoutData(new GridData(GridData.FILL_BOTH));
		tree.setFont(parent.getFont());
		fSelectedViewer.addDoubleClickListener(new IDoubleClickListener() {
            public void doubleClick(DoubleClickEvent event) {
                handleRemovePressed();
            }
        });
		fSelectedViewer.addSelectionChangedListener(new ISelectionChangedListener() {
			public void selectionChanged(SelectionChangedEvent event) {
				updateSelectedButtons();
			}
		});
	}
	
	public void createButtons(Composite parent) {
		Composite buttonComposite= new Composite(parent, SWT.NONE);
		buttonComposite.setLayout(new GridLayout());
		buttonComposite.setLayoutData(new GridData());
		buttonComposite.setFont(parent.getFont());
		
		fAddButton= SWTUtil.createPushButton(buttonComposite, BreakpointGroupMessages.getString("GroupBreakpointsByDialog.2"), null); //$NON-NLS-1$
		fAddButton.addSelectionListener(fSelectionListener);
		
		fRemoveButton= SWTUtil.createPushButton(buttonComposite, BreakpointGroupMessages.getString("GroupBreakpointsByDialog.4"), null); //$NON-NLS-1$
		fRemoveButton.addSelectionListener(fSelectionListener);
		
		fMoveUpButton= SWTUtil.createPushButton(buttonComposite, BreakpointGroupMessages.getString("GroupBreakpointsByDialog.5"), null); //$NON-NLS-1$
		fMoveUpButton.addSelectionListener(fSelectionListener);
		
		fMoveDownButton= SWTUtil.createPushButton(buttonComposite, BreakpointGroupMessages.getString("GroupBreakpointsByDialog.6"), null); //$NON-NLS-1$
		fMoveDownButton.addSelectionListener(fSelectionListener);
	    
	}

	/**
	 * Returns the organizers chosen by the user. The order
	 * of the list is the order that the organizers should be displayed
	 * in the breakpoints view.
	 * @return the breakpoint organizers chosen by the user
	 */
	public IBreakpointOrganizer[] getOrganizers() {
		return (IBreakpointOrganizer[]) fResult.toArray(new IBreakpointOrganizer[fResult.size()]);
	}
	
	/**
	 * When the user presses OK, convert the tree selection into a list.
	 */
	protected void okPressed() {
		Object[] factories= fSelectedOrganizersProvider.getElements(null);
		while (factories.length > 0) {
			Object factory= factories[0];
			fResult.add(factory);
			factories= fSelectedOrganizersProvider.getChildren(factory);
		}
		super.okPressed();
	}
	
	/**
	 * Moves the selected item from the list of "available" factories
	 * to the tree of "selected" factories.
	 */
	public void handleAddPressed() {
		IStructuredSelection selection= (IStructuredSelection) fAvailableViewer.getSelection();
		if (selection.size() < 1) {
			return;
		}
		Iterator iter= selection.iterator();
		while (iter.hasNext()) {
			fSelectedOrganizersProvider.addSelected((IBreakpointOrganizer) iter.next());
		}
		updateViewers();
	}
	
	/**
	 * Moves the selected item from the tree of "selected" factories
	 * to the list of "available" factories.
	 */
	public void handleRemovePressed() {
		IStructuredSelection selection= (IStructuredSelection) fSelectedViewer.getSelection();
		if (selection.size() < 1) {
			return;
		}
		Iterator iter= selection.iterator();
		while (iter.hasNext()) {
			fAvailableOrganizersProvider.addAvailable((IBreakpointOrganizer) iter.next());
		}
		updateViewers();
	}
	
	/**
	 * Moves each selected item up in the tree of selected containers
	 */
	public void handleMoveUpPressed() {
		IStructuredSelection selection = (IStructuredSelection) fSelectedViewer.getSelection();
		Iterator iter = selection.iterator();
		while (iter.hasNext()) {
			fSelectedOrganizersProvider.moveUp(iter.next());
		}
		updateViewers();
	}
	
	/**
	 * Moves each selected item down in the tree of selected containers
	 */
	public void handleMoveDownPressed() {
		IStructuredSelection selection = (IStructuredSelection) fSelectedViewer.getSelection();
		Object[] elements= selection.toArray();
		for (int i= elements.length - 1; i >= 0; i--) {
			fSelectedOrganizersProvider.moveDown(elements[i]);
		}
		updateViewers();
	}
	
	/**
	 * Fully refreshes and updates all viewers and buttons.
	 */
	public void updateViewers() {
		fAvailableViewer.refresh();
		fSelectedViewer.refresh();
		fSelectedViewer.expandAll();
		updateAddButton();
		updateSelectedButtons();
	}
	
	/**
	 * Updates all buttons associated with the tree of selected containers.
	 */
	public void updateSelectedButtons() {
		updateRemoveButton();
		updateMoveUpButton();
		updateMoveDownButton();
	}
	
	public void updateAddButton() {
		IStructuredSelection selection = (IStructuredSelection) fAvailableViewer.getSelection();
		fAddButton.setEnabled(selection.size() > 0);
	}
	
	public void updateRemoveButton() {
		IStructuredSelection selection = (IStructuredSelection) fSelectedViewer.getSelection();
		fRemoveButton.setEnabled(selection.size() > 0);
	}
	
	public void updateMoveUpButton() {
		boolean enabled= true;
		IStructuredSelection selection = (IStructuredSelection) fSelectedViewer.getSelection();
		if (selection.size() == 0) {
			enabled= false;
		} else {
			Object firstSelected= selection.getFirstElement();
			Object parent= fSelectedOrganizersProvider.getParent(firstSelected);
			if (!(parent instanceof IBreakpointOrganizer)) {
				enabled= false;
			}
		}
		fMoveUpButton.setEnabled(enabled);
	}
	
	public void updateMoveDownButton() {
		boolean enabled= true;
		IStructuredSelection selection = (IStructuredSelection) fSelectedViewer.getSelection();
		if (selection.size() == 0) {
			enabled= false;
		} else {
			Object lastSelected= selection.toList().get(selection.size() - 1);
			Object[] children= fSelectedOrganizersProvider.getChildren(lastSelected);
			if (children.length < 1) {
				enabled= false;
			}
		}
		fMoveDownButton.setEnabled(enabled);
	}
	
	/**
	 * Content provider that provides the list of breakpoint organaisers
     * that are available but not currently selected.
	 */
	private class AvailableOrganizersProvider implements IStructuredContentProvider {
		protected List availableOrganziers= new ArrayList();
		
		public void addAvailable(IBreakpointOrganizer organizer) {
            availableOrganziers.add(organizer);
			fSelectedOrganizersProvider.selectedOrganizers.remove(organizer);
		}
		
		public Object[] getElements(Object inputElement) {
			return availableOrganziers.toArray();
		}
		
		public void dispose() {
		}
		public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
		}
	}
	
	/**
	 * Content provider that returns the selected breakpoint organizers
	 * as a tree. This tree shows the list of organzizers as they will
	 * appear in the breakpoints view.
	 */
	private class SelectedOrganizerProvider implements ITreeContentProvider {
		protected List selectedOrganizers= new ArrayList();
		
		public void addSelected(IBreakpointOrganizer organizer) {
            selectedOrganizers.add(organizer);
			fAvailableOrganizersProvider.availableOrganziers.remove(organizer);
		}
		
		public void moveUp(Object object) {
			int index = selectedOrganizers.indexOf(object);
			if (index > 0) {
                selectedOrganizers.remove(object);
                selectedOrganizers.add(index - 1, object);
			}
		}
		
		public void moveDown(Object object) {
			int index = selectedOrganizers.indexOf(object);
			if (index < selectedOrganizers.size() - 1) {
                selectedOrganizers.remove(object);
                selectedOrganizers.add(index + 1, object);
			}
		}

		public Object[] getChildren(Object parentElement) {
			// A factory's "child" is the next factory in the list
			int index = selectedOrganizers.indexOf(parentElement);
			if (index < selectedOrganizers.size() - 1) {
				return new Object[] { selectedOrganizers.get(index + 1) };
			}
			return new Object[0];
		}

		public Object getParent(Object element) {
			// A factory's "parent" is the factory before it
			int index = selectedOrganizers.indexOf(element);
			if (index <= 0 || selectedOrganizers.size() <= 1) {
				return null;
			}
			return selectedOrganizers.get(index - 1);
		}

		public boolean hasChildren(Object element) {
			// A factory has "children" if there are more
			// factories after it.
			int index = selectedOrganizers.indexOf(element);
			return index != -1 && index < selectedOrganizers.size() - 1;
		}

		public Object[] getElements(Object inputElement) {
			if (selectedOrganizers.size() > 0) {
				return new Object[] { selectedOrganizers.get(0) };
			}
			return new Object[0];
		}
		public void dispose() {
		}
		public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
		}
	}
	
	/**
	 * Label provider which provides text and images for breakpoint container factories
	 */
	private class BreakpointOrganzierLabelProvider extends ViewLabelProvider {
		private HashMap fImageCache= new HashMap();
		
		public String getText(Object element) {
			if (element instanceof IBreakpointOrganizer) {
				return ((IBreakpointOrganizer) element).getLabel();
			}
			return super.getText(element);
		}
		public Image getImage(Object element) {
			if (element instanceof IBreakpointOrganizer) {
				ImageDescriptor imageDescriptor = ((IBreakpointOrganizer) element).getImageDescriptor();
				if (imageDescriptor != null) {
					Image image = (Image) fImageCache.get(imageDescriptor);
					if (image == null) {
						image= imageDescriptor.createImage();
						if (image != null) {
							fImageCache.put(imageDescriptor, image);
						}
					}
					return image;
				}
			}
			return super.getImage(element);
		}
		public void dispose() {
			Iterator imageIter = fImageCache.values().iterator();
			while (imageIter.hasNext()) {
				((Image) imageIter.next()).dispose();
			}
			super.dispose();
		}
	}
	
    /*
     * (non-Javadoc)
     * 
     * @see org.eclipse.jface.window.Window#configureShell(org.eclipse.swt.widgets.Shell)
     */
    protected void configureShell(Shell shell) {
        super.configureShell(shell);
        shell.setText(BreakpointGroupMessages.getString("GroupBreakpointsByDialog.7")); //$NON-NLS-1$
    }
}
