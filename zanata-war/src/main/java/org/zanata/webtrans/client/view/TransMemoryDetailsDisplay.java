package org.zanata.webtrans.client.view;

import java.util.Date;
import java.util.List;

import org.zanata.common.ContentState;

import net.customware.gwt.presenter.client.widget.WidgetDisplay;

/**
 * @author Patrick Huang <a
 *         href="mailto:pahuang@redhat.com">pahuang@redhat.com</a>
 */
public interface TransMemoryDetailsDisplay extends WidgetDisplay {
    void hide();

    void show();

    int getSelectedDocumentIndex();

    void clearDocs();

    void addDoc(String text);

    void setSourceComment(String sourceComment);

    void setTargetComment(String targetComment);

    void setProjectIterationName(String projectIterationName);

    void setDocumentName(String documentName);

    void setLastModified(String lastModifiedBy, Date lastModifiedDate);

    void clearSourceAndTarget();

    void setSource(List<String> sourceContents);

    void setTarget(List<String> targetContents);

    void setListener(Listener listener);

    interface Listener {

        void dismissTransMemoryDetails();

        void onDocumentListBoxChanged();
    }

    void setState(ContentState state);
}
