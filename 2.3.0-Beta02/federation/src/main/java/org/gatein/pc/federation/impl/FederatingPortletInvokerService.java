/*
 * JBoss, a division of Red Hat
 * Copyright 2011, Red Hat Middleware, LLC, and individual
 * contributors as indicated by the @authors tag. See the
 * copyright.txt in the distribution for a full listing of
 * individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.gatein.pc.federation.impl;

import org.gatein.common.logging.Logger;
import org.gatein.common.logging.LoggerFactory;
import org.gatein.pc.api.InvokerUnavailableException;
import org.gatein.pc.api.NoSuchPortletException;
import org.gatein.pc.api.Portlet;
import org.gatein.pc.api.PortletContext;
import org.gatein.pc.api.PortletInvoker;
import org.gatein.pc.api.PortletInvokerException;
import org.gatein.pc.api.PortletStateType;
import org.gatein.pc.api.PortletStatus;
import org.gatein.pc.api.StatefulPortletContext;
import org.gatein.pc.api.invocation.PortletInvocation;
import org.gatein.pc.api.invocation.response.PortletInvocationResponse;
import org.gatein.pc.api.state.DestroyCloneFailure;
import org.gatein.pc.api.state.PropertyChange;
import org.gatein.pc.api.state.PropertyMap;
import org.gatein.pc.federation.FederatedPortletInvoker;
import org.gatein.pc.federation.FederatingPortletInvoker;
import org.gatein.pc.federation.NullInvokerHandler;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author <a href="mailto:julien@jboss.org">Julien Viet</a>
 * @author <a href="mailto:chris.laprun@jboss.com">Chris Laprun</a>
 * @version $Revision: 5918 $
 * @since 2.4
 */
public class FederatingPortletInvokerService implements FederatingPortletInvoker
{

   /** . */
   private static final Logger log = LoggerFactory.getLogger(FederatingPortletInvokerService.class);

   /** The registred FederatedPortletInvokers. */
   private volatile Map<String, FederatedPortletInvoker> registry = new HashMap<String, FederatedPortletInvoker>();

   private NullInvokerHandler nullHandler = NullInvokerHandler.DEFAULT_HANDLER;

   public synchronized FederatedPortletInvoker registerInvoker(String federatedId, PortletInvoker federatedInvoker)
   {
      if (federatedId == null)
      {
         throw new IllegalArgumentException("No null id");
      }
      if (federatedInvoker == null)
      {
         throw new IllegalArgumentException("No null invoker");
      }
      if (registry.containsKey(federatedId))
      {
         throw new IllegalArgumentException("Attempting dual registration of " + federatedId);
      }
      Map<String, FederatedPortletInvoker> copy = new HashMap<String, FederatedPortletInvoker>(registry);
      FederatedPortletInvokerService invoker = new FederatedPortletInvokerService(this, federatedId, federatedInvoker);
      copy.put(federatedId, invoker);
      registry = copy;
      return invoker;
   }

   public synchronized void unregisterInvoker(String federatedId)
   {
      if (federatedId == null)
      {
         throw new IllegalArgumentException("No null id accepted");
      }
      if (!registry.containsKey(federatedId))
      {
         throw new IllegalArgumentException("Attempting to unregister unknown invoker " + federatedId);
      }
      Map<String, FederatedPortletInvoker> copy = new HashMap<String, FederatedPortletInvoker>(registry);
      copy.remove(federatedId);
      registry = copy;
   }

   public FederatedPortletInvoker getFederatedInvoker(String federatedId) throws IllegalArgumentException
   {
      if (federatedId == null)
      {
         throw new IllegalArgumentException("No null id provided");
      }
      return registry.get(federatedId);
   }

   public Collection<FederatedPortletInvoker> getFederatedInvokers()
   {
      return registry.values();
   }

   // PortletInvoker implementation ************************************************************************************

   public Set<Portlet> getPortlets() throws PortletInvokerException
   {
      return getPortlets(false);
   }

   private Set<Portlet> getPortlets(boolean remoteOnly) throws PortletInvokerException
   {
      LinkedHashSet<Portlet> portlets = new LinkedHashSet<Portlet>();
      for (FederatedPortletInvoker federated : registry.values())
      {
         // if we're only interested in remote portlets, skip the local invoker.
         if (remoteOnly && LOCAL_PORTLET_INVOKER_ID.equals(federated.getId()))
         {
            continue;
         }

         try
         {
            Set<Portlet> offeredPortlets = federated.getPortlets();
            portlets.addAll(offeredPortlets);
         }
         catch (InvokerUnavailableException e)
         {
            Throwable cause = e.getCause();
            log.debug(e.fillInStackTrace());
            log.warn("PortletInvoker with id: " + federated.getId() + " is not available.\nReason: " + e.getMessage()
               + "\nCaused by:\n" + (cause == null ? e : cause));
         }
      }
      return portlets;
   }

   public Set<Portlet> getLocalPortlets() throws PortletInvokerException
   {
      PortletInvoker local = registry.get(PortletInvoker.LOCAL_PORTLET_INVOKER_ID);

      return local.getPortlets();
   }

   public Set<Portlet> getRemotePortlets() throws PortletInvokerException
   {
      return getPortlets(true);
   }

   public Portlet getPortlet(PortletContext compoundPortletContext) throws IllegalArgumentException, PortletInvokerException
   {
      PortletInvoker federated = getFederatedPortletInvokerFor(compoundPortletContext);
      return federated.getPortlet(compoundPortletContext);
   }

   public PortletStatus getStatus(PortletContext portletContext) throws IllegalArgumentException, PortletInvokerException
   {
      PortletInvoker federated = getFederatedPortletInvokerFor(portletContext);
      return federated.getStatus(portletContext);
   }

   public PortletInvocationResponse invoke(PortletInvocation invocation) throws PortletInvokerException
   {
      PortletContext compoundPortletContext = invocation.getTarget();
      PortletInvoker federated = getFederatedPortletInvokerFor(compoundPortletContext);
      return federated.invoke(invocation);
   }

   public PortletContext createClone(PortletStateType stateType, PortletContext compoundPortletContext) throws PortletInvokerException
   {
      PortletInvoker federated = getFederatedPortletInvokerFor(compoundPortletContext);
      return federated.createClone(stateType, compoundPortletContext);
   }

   public List<DestroyCloneFailure> destroyClones(List<PortletContext> portletContexts) throws IllegalArgumentException, PortletInvokerException, UnsupportedOperationException
   {
      if (portletContexts == null)
      {
         throw new IllegalArgumentException("No null list accepted");
      }
      if (portletContexts.size() == 0)
      {
         return Collections.emptyList();
      }

      // Get the invoker and check that we address only one invoker (for now)
      PortletInvoker invoker = null;
      for (PortletContext compoundPortletContext : portletContexts)
      {
         PortletInvoker federated = getFederatedPortletInvokerFor(compoundPortletContext);
         if (invoker == null)
         {
            invoker = federated;
         }
         else if (!invoker.equals(federated))
         {
            throw new PortletInvokerException("Cannot destroy portlet lists that requires more than one federated invoker");
         }
      }

      //
      return invoker.destroyClones(portletContexts);
   }

   public PropertyMap getProperties(PortletContext compoundPortletContext, Set<String> keys) throws PortletInvokerException
   {
      PortletInvoker federated = getFederatedPortletInvokerFor(compoundPortletContext);
      return federated.getProperties(compoundPortletContext, keys);
   }

   public PropertyMap getProperties(PortletContext compoundPortletContext) throws PortletInvokerException
   {
      PortletInvoker federated = getFederatedPortletInvokerFor(compoundPortletContext);
      return federated.getProperties(compoundPortletContext);
   }

   public PortletContext setProperties(PortletContext compoundPortletContext, PropertyChange[] changes) throws IllegalArgumentException, PortletInvokerException, UnsupportedOperationException
   {
      PortletInvoker federated = getFederatedPortletInvokerFor(compoundPortletContext);
      return federated.setProperties(compoundPortletContext, changes);
   }

   public PortletContext exportPortlet(PortletStateType stateType, PortletContext compoundPortletContext)
      throws PortletInvokerException
   {
      PortletInvoker federated = getFederatedPortletInvokerFor(compoundPortletContext);
      return federated.exportPortlet(stateType, compoundPortletContext);
   }

   public PortletContext importPortlet(PortletStateType stateType, PortletContext compoundPortletContext)
      throws PortletInvokerException
   {
      PortletInvoker federated = getFederatedPortletInvokerFor(compoundPortletContext);
      return federated.importPortlet(stateType, compoundPortletContext);
   }

   public synchronized void setNullInvokerHandler(NullInvokerHandler nullHandler)
   {
      if (nullHandler == null)
      {
         this.nullHandler = NullInvokerHandler.DEFAULT_HANDLER;
      }
      else
      {
         this.nullHandler = nullHandler;
      }
   }

   // Support methods **************************************************************************************************

   /**
    * Retrieves the portlet invoker associated with the specified compound portlet id or null if it is not found.
    *
    * @param compoundPortletContext the portlet context for which the invoker is to be retrieved
    * @return the portlet invoker associated with the specified compound portlet id
    * @throws IllegalArgumentException if the compound portlet id is not well formed or null
    * @throws NoSuchPortletException   if not such portlet exist
    */
   protected FederatedPortletInvoker getFederatedPortletInvokerFor(PortletContext compoundPortletContext) throws IllegalArgumentException, NoSuchPortletException
   {
      if (compoundPortletContext == null)
      {
         throw new IllegalArgumentException("No null portlet id accepted");
      }

      PortletContext.PortletContextComponents components = compoundPortletContext.getComponents();
      final String compoundPortletId = compoundPortletContext.getId();
      if (components == null)
      {
         // force intepretation
         compoundPortletContext = PortletContext.createPortletContext(compoundPortletId, true);
         components = compoundPortletContext.getComponents();
      }

      final String invokerId = components.getInvokerName();
      if (invokerId == null)
      {
         throw new IllegalArgumentException("Bad portlet id format " + compoundPortletId);
      }

      FederatedPortletInvoker federated = registry.get(invokerId);
      if (federated == null)
      {
         return nullHandler.resolvePortletInvokerFor(compoundPortletId, invokerId, this);
      }

      //
      return federated;
   }
}