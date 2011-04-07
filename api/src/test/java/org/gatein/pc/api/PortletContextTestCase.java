/*
* JBoss, a division of Red Hat
* Copyright 2008, Red Hat Middleware, LLC, and individual contributors as indicated
* by the @authors tag. See the copyright.txt in the distribution for a
* full listing of individual contributors.
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

package org.gatein.pc.api;

import junit.framework.TestCase;

/**
 * @author <a href="mailto:chris.laprun@jboss.com">Chris Laprun</a>
 * @version $Revision$
 */
public class PortletContextTestCase extends TestCase
{
   public void testGetComponents()
   {
      PortletContext context = PortletContext.createPortletContext("/applicationName.portletName");
      assertEquals("/applicationName.portletName", context.getId());
      PortletContext.PortletContextComponents components = context.getComponents();
      assertNotNull(components);
      assertNull(components.getInvokerName());
      assertEquals("applicationName", components.getApplicationName());
      assertEquals("portletName", components.getPortletName());

      context = PortletContext.createPortletContext("\t\t\n    /applicationName.portletName   \t");
      assertEquals("/applicationName.portletName", context.getId());
      components = context.getComponents();
      assertNotNull(components);
      assertNull(components.getInvokerName());
      assertEquals("applicationName", components.getApplicationName());
      assertEquals("portletName", components.getPortletName());

      try
      {
         PortletContext.createPortletContext("/");
         fail("invalid");
      }
      catch (IllegalArgumentException e)
      {
         // expected
      }

      context = PortletContext.createPortletContext("applicationName.portletName");
      assertEquals("applicationName.portletName", context.getId());
      components = context.getComponents();
      assertNotNull(components);
      assertEquals("applicationName", components.getInvokerName());
      assertNull(components.getApplicationName());
      assertEquals("portletName", components.getPortletName());

      context = PortletContext.createPortletContext("/applicationName.portlet.Name");
      assertEquals("/applicationName.portlet.Name", context.getId());
      components = context.getComponents();
      assertNotNull(components);
      assertNull(components.getInvokerName());
      assertEquals("applicationName", components.getApplicationName());
      assertEquals("portlet.Name", components.getPortletName());

      try
      {
         PortletContext.createPortletContext("/.");
         fail();
      }
      catch (IllegalArgumentException e)
      {
         // expected
      }

      context = PortletContext.createPortletContext("/  applicationName\t.  portlet Name");
      assertEquals("/applicationName.portlet Name", context.getId());
      components = context.getComponents();
      assertNotNull(components);
      assertNull(components.getInvokerName());
      assertEquals("applicationName", components.getApplicationName());
      assertEquals("portlet Name", components.getPortletName());
   }

   public void testPortletContextWithInvokerId()
   {
      PortletContext context = PortletContext.createPortletContext("local./foo.bar");
      assertEquals("local./foo.bar", context.getId());
      PortletContext.PortletContextComponents components = context.getComponents();
      assertNotNull(components);
      assertEquals("local", components.getInvokerName());
      assertEquals("foo", components.getApplicationName());
      assertEquals("bar", components.getPortletName());

      context = PortletContext.createPortletContext("   local\t  .  /  foo \t. \t\n bar");
      assertEquals("local./foo.bar", context.getId());
      components = context.getComponents();
      assertNotNull(components);
      assertEquals("local", components.getInvokerName());
      assertEquals("foo", components.getApplicationName());
      assertEquals("bar", components.getPortletName());

      context = PortletContext.createPortletContext("local.foo.bar");
      assertEquals("local.foo.bar", context.getId());
      components = context.getComponents();
      assertNotNull(components);
      assertEquals("local", components.getInvokerName());
      assertNull(components.getApplicationName());
      assertEquals("foo.bar", components.getPortletName());

      context = PortletContext.createPortletContext("local./foo");
      assertEquals("local./foo", context.getId());
      components = context.getComponents();
      assertNotNull(components);
      assertEquals("local", components.getInvokerName());
      assertNull(components.getApplicationName());
      assertEquals("/foo", components.getPortletName());
   }

   public void testCreateFromComponents()
   {
      PortletContext fromId = PortletContext.createPortletContext("/applicationName.portletName");

      PortletContext context = PortletContext.createPortletContext("applicationName", "portletName");
      assertEquals("/applicationName.portletName", context.getId());
      PortletContext.PortletContextComponents components = context.getComponents();
      assertNotNull(components);
      assertNull(components.getInvokerName());
      assertEquals("applicationName", components.getApplicationName());
      assertEquals("portletName", components.getPortletName());
      assertEquals(context, fromId);
   }

   public void testShouldProperlyHandleApplicationNameStartingWithSlash()
   {
      PortletContext fromId = PortletContext.createPortletContext("/applicationName.portletName");

      PortletContext context = PortletContext.createPortletContext("/applicationName", "portletName");
      assertEquals("/applicationName.portletName", context.getId());
      PortletContext.PortletContextComponents components = context.getComponents();
      assertNotNull(components);
      assertNull(components.getInvokerName());
      assertEquals("applicationName", components.getApplicationName());
      assertEquals("portletName", components.getPortletName());
      assertEquals(context, fromId);
   }

   public void testCreateFromNullOrEmpty()
   {
      try
      {
         PortletContext.createPortletContext(null);
         fail("Attempting to create a PortletContext from null should have thrown an exception.");
      }
      catch (IllegalArgumentException e)
      {
         // expected
      }

      try
      {
         PortletContext.createPortletContext("");
         fail("Attempting to create a PortletContext from empty String should have thrown an exception.");
      }
      catch (IllegalArgumentException e)
      {
         // expected
      }
   }
}
