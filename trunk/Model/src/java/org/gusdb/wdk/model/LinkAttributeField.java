package org.gusdb.wdk.model;

import java.util.ArrayList;
import java.util.List;

public class LinkAttributeField extends AttributeField {

    private List<WdkModelText> urls;
    private String url;

    private String visible;

    public LinkAttributeField() {
        urls = new ArrayList<WdkModelText>();
    }

    public void setVisible(String visible) {
        this.visible = visible;
    }

    public void addUrl(WdkModelText url) {
        this.urls.add(url);
    }

    String getUrl() {
        return url;
    }

    String getVisible() {
        return visible;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.gusdb.wdk.model.WdkModelBase#excludeResources(java.lang.String)
     */
    @Override
    public void excludeResources(String projectId) {
        // exclude urls
        for (WdkModelText url : urls) {
            if (url.include(projectId)) {
                this.url = url.getText();
                break;
            }
        }
        urls = null;
    }
}
