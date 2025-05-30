/*******************************************************************************
 * Copyright (c) 2000, 2025 IBM Corporation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.internal.core;

import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.jobs.ISchedulingRule;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.JavaModelException;

/*
 * Abstract class for operations that change the classpath
 */
public abstract class ChangeClasspathOperation extends JavaModelOperation {

	protected boolean canChangeResources;

	public ChangeClasspathOperation(IJavaElement[] elements, boolean canChangeResources) {
		super(elements);
		this.canChangeResources = canChangeResources;
	}

	@Override
	protected boolean canModifyRoots() {
		// changing the classpath can modify roots
		return true;
	}

	/*
	 * The resolved classpath of the given project may have changed:
	 * - generate a delta
	 * - trigger indexing
	 * - update project references
	 * - create resolved classpath markers
	 */
	protected void classpathChanged(ClasspathChange change, boolean refreshExternalFolder) throws JavaModelException {
		// reset the project's caches early since some clients rely on the project's caches being up-to-date when run inside an IWorkspaceRunnable
		// (see https://bugs.eclipse.org/bugs/show_bug.cgi?id=212769#c5 )
		JavaProject project = change.project;
		project.resetCaches();

		if (this.canChangeResources) {

			// delta, indexing and classpath markers are going to be created by the delta processor
			// while handling the resource change (either .classpath change, or project touched)

			project.getProject().clearCachedDynamicReferences();
			// and ensure that external folders are updated as well
			new ExternalFolderChange(project, change.oldResolvedClasspath).updateExternalFoldersIfNecessary(refreshExternalFolder, null);

			// workaround for https://bugs.eclipse.org/bugs/show_bug.cgi?id=177922
			if (isTopLevelOperation() && !ResourcesPlugin.getWorkspace().isTreeLocked()) {
				new ClasspathValidation(project).validate();
			}

		} else {
			DeltaProcessingState state = JavaModelManager.getDeltaState();
			JavaElementDelta delta = new JavaElementDelta(getJavaModel());
			int result = change.generateDelta(delta, true/*add classpath change*/);
			if ((result & ClasspathChange.HAS_DELTA) != 0) {
				// create delta
				addDelta(delta);

				// need to recompute root infos
				state.rootsAreStale = true;

				// ensure indexes are updated
				change.requestIndexing();

				// ensure classpath is validated on next build
				state.addClasspathValidation(project);
			}
			if ((result & ClasspathChange.HAS_PROJECT_CHANGE) != 0) {
				// ensure project references are updated on next build
				project.getProject().clearCachedDynamicReferences();
				state.addProjectReferenceChange(project);
			}
			if ((result & ClasspathChange.HAS_LIBRARY_CHANGE) != 0) {
				// ensure external folders are updated on next build
				state.addExternalFolderChange(project, change.oldResolvedClasspath);
			}
		}
	}

	@Override
	protected ISchedulingRule getSchedulingRule() {
		return null; // no lock taken while changing classpath
	}

	@Override
	public boolean isReadOnly() {
		return !this.canChangeResources;
	}

}
