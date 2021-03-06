package edu.stanford.bmir.protege.web.client.ui.tab;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.shared.SimpleEventBus;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtext.client.widgets.Component;
import com.gwtext.client.widgets.Panel;
import com.gwtext.client.widgets.event.PanelListener;
import com.gwtext.client.widgets.event.PanelListenerAdapter;
import com.gwtext.client.widgets.layout.ColumnLayoutData;
import com.gwtext.client.widgets.layout.FitLayout;
import com.gwtext.client.widgets.portal.Portal;
import com.gwtext.client.widgets.portal.PortalColumn;
import com.gwtext.client.widgets.portal.Portlet;
import edu.stanford.bmir.protege.web.client.project.Project;
import edu.stanford.bmir.protege.web.client.rpc.data.layout.PortletConfiguration;
import edu.stanford.bmir.protege.web.client.rpc.data.layout.TabColumnConfiguration;
import edu.stanford.bmir.protege.web.client.rpc.data.layout.TabConfiguration;
import edu.stanford.bmir.protege.web.client.ui.generated.UIFactory;
import edu.stanford.bmir.protege.web.client.ui.ontology.classes.ClassesTab;
import edu.stanford.bmir.protege.web.client.ui.portlet.AbstractEntityPortlet;
import edu.stanford.bmir.protege.web.client.ui.portlet.EntityPortlet;
import edu.stanford.bmir.protege.web.shared.selection.SelectionModel;

import java.util.*;

/**
 * Abstract class that should be extended by all tabs that can be added to the
 * main UI of WebProtege.
 * <p/>
 * The tab has a reference to all the portlets that are currently part of the
 * tab and a reference to the controlling portlet -> the portlet that provides
 * the selection for the other portlets.
 *
 * @author Tania Tudorache <tudorache@stanford.edu>
 */
// TODO: reordering of portlets in a column at drang-n-drop does not work yet
public abstract class AbstractTab extends Portal {

    protected Project project;
    protected Portal portal;

    private TabConfiguration tabConfiguration;
    private final LinkedHashMap<PortalColumn, List<EntityPortlet>> columnToPortletsMap;
    private final LinkedHashMap<TabColumnConfiguration, PortalColumn> tabColumnConfigToColumn;

    private Comparator<PortletConfiguration> portletComparator;
    // TODO: cache the portlets for performance

    private PanelListener portletDestroyListener;

    private final SelectionModel selectionModel;

    public AbstractTab(final SelectionModel selectionModel, final Project project) {
        super();
        this.project = project;
        this.portal = new Portal();
        this.columnToPortletsMap = new LinkedHashMap<>();
        this.tabColumnConfigToColumn = new LinkedHashMap<>();

        this.portletDestroyListener = getPortletDestroyListener();

        this.selectionModel = selectionModel;
        addListener(new PanelListenerAdapter(){
            @Override
            public void onActivate(Panel panel) {
                // Tell the portlets to update themselves
                for(EntityPortlet entityPortlet : getPortlets()) {
                    if(entityPortlet instanceof AbstractEntityPortlet) {
                        ((AbstractEntityPortlet) entityPortlet).handleActivated();
                    }
                }
            }
        });
    }

    public SelectionModel getSelectionModel() {
        return selectionModel;
    }

    protected static ColumnLayoutData[] getDefaultColumnData(final int columnCount) {
        final ColumnLayoutData[] colLayout = new ColumnLayoutData[columnCount];
        for (int i = 0; i < columnCount; i++) {
            colLayout[i] = new ColumnLayoutData(1 / columnCount);
        }
        return colLayout;
    }

    protected PanelListener getPortletDestroyListener() {
        if (portletDestroyListener == null) {
            this.portletDestroyListener = new PanelListenerAdapter() {
                @Override
                public void onDestroy(final Component component) {
                    if (component instanceof EntityPortlet) {
                        removePortlet((EntityPortlet) component);
                    }
                    super.onDestroy(component);
                }
            };
        }
        return portletDestroyListener;
    }

    private void onPermissionsChanged(final Collection<String> permissions) {

    }

    public int getColumnCount() {
        return columnToPortletsMap.size();
    }

    public List<PortalColumn> getColumns() {
        return new ArrayList<PortalColumn>(columnToPortletsMap.keySet());
    }

    public List<EntityPortlet> getPortlets() {
        final List<EntityPortlet> portlets = new ArrayList<EntityPortlet>();
        for (final List<EntityPortlet> portletList : columnToPortletsMap.values()) {
            portlets.addAll(portletList);
        }
        return portlets;
    }

    public void addPortlet(final EntityPortlet portlet, final int column) {
        GWT.log("Adding portlet to tab: " + portlet.getClass().getName());
        final TabColumnConfiguration col = getTabColumnConfigurationAt(column);
        if (col == null) {
            GWT.log("Column does not exist: " + column, null);
            return;
        }
        final PortletConfiguration portletConfiguration = ((AbstractEntityPortlet) portlet).getPortletConfiguration();
        addPortletToColumn(portlet, col, portletConfiguration == null ? project.getLayoutManager().createPortletConfiguration(portlet) : portletConfiguration, true);
    }

    public void removePortlet(final EntityPortlet portlet) {
        for (final PortalColumn col : columnToPortletsMap.keySet()) {
            final List<EntityPortlet> portlets = columnToPortletsMap.get(col);
            if (portlets.contains(portlet)) {
                portlets.remove(portlet);
                removePortletFromTabConfig(portlet);
                ((Portlet) portlet).hide();
                ((Portlet) portlet).destroy();
                // how to remove the destroy listener?
            }
        }
    }

    protected void removePortletFromTabConfig(final EntityPortlet portlet) {
        for (final TabColumnConfiguration column : tabColumnConfigToColumn.keySet()) {
            final PortalColumn portalColumn = tabColumnConfigToColumn.get(column);
            final List<PortletConfiguration> portlets = column.getPortlets();
            final Component[] comps = portalColumn.getComponents();
            for (int i = 0; i < comps.length; i++) {
                if (comps[i].equals(portlet)) {
                    if (portlets.size() > i) { // TODO: check should not be
                        // needed, but it seems that
                        // portlets are destroyed several
                        // times
                        final PortletConfiguration portletConfig = portlets.get(i);
                        if (portletConfig.getName().equals(portlet.getClass().getName())) {
                            portlets.remove(portletConfig);
                        }
                    }
                }
            }
        }
    }

    public PortalColumn getPortalColumnAt(final int index) {
        final TabColumnConfiguration config = getTabColumnConfigurationAt(index);
        return config == null ? null : tabColumnConfigToColumn.get(config);
    }

    public TabColumnConfiguration getTabColumnConfigurationAt(final int index) {
        final Collection<TabColumnConfiguration> tabCols = tabColumnConfigToColumn.keySet();
        if (tabCols.size() < index) {
            return null;
        }
        int i = -1;
        for (final TabColumnConfiguration tabColumnConfiguration : tabCols) {
            final TabColumnConfiguration col = tabColumnConfiguration;
            i++;
            if (i == index) {
                return col;
            }
        }
        return null;
    }

    public EntityPortlet getControllingPortlet() {
        return null;
    }

    public void setControllingPortlet(final EntityPortlet newControllingPortlet) {
//        if (controllingPortlet != null) {
//            if (controllingPortlet.equals(newControllingPortlet)) {
//                return;
//            }
//            controllingPortlet.removeSelectionListener(selectionControllingListener);
//            ((AbstractEntityPortlet)controllingPortlet).updateIcon(false);
//        }
//        controllingPortlet = newControllingPortlet;
//        if (controllingPortlet != null) {
//            controllingPortlet.addSelectionListener(selectionControllingListener);
//            ((AbstractEntityPortlet)controllingPortlet).updateIcon(true);
//        }
    }

    public TabConfiguration getTabConfiguration() {
        return tabConfiguration;
    }

    public void setTabConfiguration(final TabConfiguration tabConfig) {
        this.tabConfiguration = tabConfig;
    }

    public void setup() {
        setLayout(new FitLayout());
        setHideBorders(true);

        // if configuration is null, initialize the default UI
        if (tabConfiguration == null) {
            tabConfiguration = getDefaultTabConfiguration();
        }

        setTitle(getLabel());
        setClosable(tabConfiguration.getClosable());
        addColumns();
        addPorteltsToColumns();
        setupControllingPortlet();

        add(portal);
    }


    protected void addColumns() {
        for (int i = 0; i < tabConfiguration.getColumns().size(); i++) {
            addColumn(tabConfiguration.getColumns().get(i));
        }
    }

    protected void addColumn(final TabColumnConfiguration colConfig) {
        final float width = colConfig.getWidth();
        final PortalColumn portalColumn = new PortalColumn();
        portalColumn.setPaddings(10, 10, 10, 10);
        portal.add(portalColumn, new ColumnLayoutData(width));
        columnToPortletsMap.put(portalColumn, new ArrayList<EntityPortlet>());
        tabColumnConfigToColumn.put(colConfig, portalColumn);
    }

    protected void addPorteltsToColumns() {
        for (final TabColumnConfiguration tabColumnConfiguration : tabColumnConfigToColumn.keySet()) {
            addPortletsToColumn(tabColumnConfiguration);
        }
    }

    protected void addPortletsToColumn(TabColumnConfiguration tabColumnConfiguration) {
        List<PortletConfiguration> portlets = tabColumnConfiguration.getPortlets();
        portlets = getSortedPortlets(portlets);
        tabColumnConfiguration.setPortlets(portlets);
        for (final PortletConfiguration portletConfiguration : portlets) {
            addPortletToColumn(tabColumnConfiguration, portletConfiguration, false);
        }
    }

    protected List<PortletConfiguration> getSortedPortlets(List<PortletConfiguration> portlets) {
        Collections.sort(portlets, getPortletComparator());
        return portlets;
    }

    protected Comparator<PortletConfiguration> getPortletComparator() {
        if (portletComparator == null) {
            portletComparator = new Comparator<PortletConfiguration>() {
                public int compare(PortletConfiguration pc1, PortletConfiguration pc2) {
                    String pc1is = pc1.getIndex();
                    String pc2is = pc2.getIndex();

                    if ((pc1is == null || pc1is.length() == 0) && (pc2is == null || pc2is.length() == 0)) {
                        return 0;
                    }
                    if (pc1is == null || pc1is.length() == 0) {
                        return 1;
                    }
                    if (pc2is == null || pc2is.length() == 0) {
                        return -1;
                    }
                    return pc2is.compareTo(pc1is); //we could do an int comparison here
                }
            };
        }
        return portletComparator;
    }

    protected void addPortletToColumn(final TabColumnConfiguration tabColumnConfiguration,
                                      final PortletConfiguration portletConfiguration, final boolean updateColConfig) {
        // configuration
        final EntityPortlet portlet = createPortlet(portletConfiguration);
        if (portlet == null) {
            return;
        }
        addPortletToColumn(portlet, tabColumnConfiguration, portletConfiguration, updateColConfig);
    }

    protected void addPortletToColumn(final EntityPortlet portlet, final TabColumnConfiguration tabColumnConfiguration,
                                      final PortletConfiguration portletConfiguration, final boolean updateColConfig) {
        ((AbstractEntityPortlet) portlet).setPortletConfiguration(portletConfiguration);
        final PortalColumn portalColumn = tabColumnConfigToColumn.get(tabColumnConfiguration);
        portalColumn.add((Portlet) portlet);
        columnToPortletsMap.get(portalColumn).add(portlet);

        ((AbstractEntityPortlet) portlet).setTab(this);

        if (updateColConfig) {
            tabColumnConfiguration.addPortelt(portletConfiguration);
            adjustPortletsIndex(tabColumnConfiguration);
        }
    }

    private void adjustPortletsIndex(TabColumnConfiguration tabColumnConfiguration){
        List<PortletConfiguration> portlets = tabColumnConfiguration.getPortlets();
        int portletsCount = portlets.size();
        for (int i=0; i < portletsCount; i++)  {
            PortletConfiguration pc = portlets.get(i);
            pc.setIndex(String.valueOf(portletsCount - i - 1));
        }
    }

    protected EntityPortlet createPortlet(final PortletConfiguration portletConfiguration) {
        final String portletClassName = portletConfiguration.getName();
        final EntityPortlet portlet = UIFactory.createPortlet(selectionModel, project, portletClassName);
        if (portlet == null) {
            return null;
        }
        final int height = portletConfiguration.getHeight();
        if (height == 0) {
            ((Portlet) portlet).setAutoHeight(true);
        } else {
            ((Portlet) portlet).setHeight(height);
        }
        final int width = portletConfiguration.getWidth();
        if (width == 0) {
            ((Portlet) portlet).setAutoWidth(true);
        } else {
            ((Portlet) portlet).setWidth(width);
        }
        ((Portlet) portlet).addListener(getPortletDestroyListener());

        return portlet;
    }

    protected void setupControllingPortlet() {
        final PortletConfiguration portletConfiguration = tabConfiguration.getControllingPortlet();
        if (portletConfiguration == null) {
            setControllingPortlet(getDefaultControllingPortlet());
        } else {
            setControllingPortlet(getPortletByClassName(portletConfiguration.getName()));
        }
    }

    public EntityPortlet getPortletByClassName(final String javaClassName) {
        for (final PortalColumn portalColumn2 : columnToPortletsMap.keySet()) {
            final PortalColumn portalColumn = portalColumn2;
            final List<EntityPortlet> portlets = columnToPortletsMap.get(portalColumn);
            for (final EntityPortlet entityPortlet : portlets) {
                if (entityPortlet.getClass().getName().equals(javaClassName)) {
                    return entityPortlet;
                }
            }
        }
        return null;
    }

    protected EntityPortlet getDefaultControllingPortlet() {
        // take the first portlet from the first column
        final PortalColumn portalColumn = columnToPortletsMap.keySet().iterator().next();
        if (portalColumn == null) {
            return null;
        }
        final List<EntityPortlet> portlets = columnToPortletsMap.get(portalColumn);
        if (portlets.isEmpty()) {
            return null;
        }
        return portlets.iterator().next();
    }

    /**
     * Overwrite this method to provide a default tab configuration for a tab.
     * For example, the {@link ClassesTab} may set up in the default
     * configuration the default portlets to be shown in the UI (e.g., classes
     * tree portlet, properties portlet and restriction portlet). The default
     * tab configuration will be used if the user has not performed any
     * customization to this tab.
     * <p>
     * This method will not se the tab configuration to the return value of this
     * method. It is responsability of the calling code to make so.
     * </p>
     * <p>
     * The default implementation will return an empty tab.
     * </p>
     *
     * @return the default configuration of this tab
     */
    public TabConfiguration getDefaultTabConfiguration() {
        final TabConfiguration tabConfiguration = new TabConfiguration();
        final List<TabColumnConfiguration> colList = new ArrayList<TabColumnConfiguration>();
        final TabColumnConfiguration column = new TabColumnConfiguration();
        column.setWidth((float) 1.0);
        colList.add(column);
        tabConfiguration.setColumns(colList);
        tabConfiguration.setName(this.getClass().getName());
        return tabConfiguration;
    }

    public String getLabel() {
        final String tabLabel = tabConfiguration != null ? tabConfiguration.getLabel() : null;
        return tabLabel == null ? "Tab" : tabLabel;
    }

    public void setLabel(final String label) {
        setTitle(label);
        if (tabConfiguration != null) {
            tabConfiguration.setLabel(label);
        }
    }
    
    public String getHeaderClass() {
        final String tabHeaderClass = tabConfiguration != null ? tabConfiguration.getHeaderCssClass() : null;
        return tabHeaderClass == null || tabHeaderClass.trim().isEmpty() ? null : tabHeaderClass;
    }
    
    public void setHeaderClass(final String className) {
        if (tabConfiguration != null) {
            tabConfiguration.setHeaderCssClass(className);
        }
    }

}
