/*******************************************************************************
 * Copyright (c) 2009 the CHISEL group and contributors.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Del Myers - initial API and implementation
 *******************************************************************************/
package ca.uvic.chisel.javasketch.ui.internal.presentation.commands;

import java.util.Map;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.ui.ISelectionService;
import org.eclipse.ui.commands.IElementUpdater;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.menus.UIElement;

import ca.uvic.chisel.javasketch.SketchPlugin;
import ca.uvic.chisel.javasketch.data.model.IActivation;
import ca.uvic.chisel.javasketch.data.model.ITraceModel;
import ca.uvic.chisel.javasketch.ui.internal.presentation.IJavaSketchPresenter;

/**
 * Collapses all activations under the focused root.
 * @author Del Myers
 */

public class CollapseAllHandler extends SketchPresentationHandler implements IElementUpdater{
	
	public static String COMMAND_ID = "ca.uvic.chisel.javasketch.command.collapseActivationCommand";
	
	/* (non-Javadoc)
	 * @see org.eclipse.core.commands.IHandler#execute(org.eclipse.core.commands.ExecutionEvent)
	 */
	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		IJavaSketchPresenter presenter = getPresenter(false);
		if (presenter != null) {
			ITraceModel model = getSelectedTraceModel(HandlerUtil.getCurrentSelection(event));
			if (model != null) {
				presenter.getSequenceChartViewer().collapseActivationsUnder(model);
			}
		}
		return null;
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.core.commands.AbstractHandler#setEnabled(java.lang.Object)
	 */
	@Override
	public void setEnabled(Object evaluationContext) {
		super.setEnabled(evaluationContext);
		boolean enabled = isEnabled();
		ISelectionService service = (ISelectionService) SketchPlugin.getDefault().getWorkbench().getService(ISelectionService.class);
		if (service == null) {
			enabled = false;
		} else {
			ITraceModel model = getSelectedTraceModel(service.getSelection());
			enabled = model instanceof IActivation;
		}
		if (enabled != isEnabled()) {
			setBaseEnabled(enabled);
		}
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.ui.commands.IElementUpdater#updateElement(org.eclipse.ui.menus.UIElement, java.util.Map)
	 */
	@SuppressWarnings("unchecked")
	@Override
	public void updateElement(UIElement element, Map parameters) {
		element.setIcon(SketchPlugin
			.imageDescriptorFromPlugin("images/etool16/collapseAll.gif"));
	}

}
