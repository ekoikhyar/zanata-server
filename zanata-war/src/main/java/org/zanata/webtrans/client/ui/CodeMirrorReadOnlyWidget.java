package org.zanata.webtrans.client.ui;

import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.TextAreaElement;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.HasText;
import com.google.gwt.user.client.ui.Widget;

public class CodeMirrorReadOnlyWidget extends Composite implements HasText
{
   private static CodeMirrorWidgetUiBinder ourUiBinder = GWT.create(CodeMirrorWidgetUiBinder.class);

   @UiField
   TextAreaElement textArea;

   private JavaScriptObject codeMirror;
   private String content;

   public CodeMirrorReadOnlyWidget()
   {
      initWidget(ourUiBinder.createAndBindUi(this));
   }

   // see http://codemirror.net/doc/manual.html#usage
   public native JavaScriptObject initCodeMirror(Element element) /*-{
      var self = this;
      var codeMirrorEditor = $wnd.CodeMirror.fromTextArea(element, {
         lineNumbers: true,
         lineWrapping: true,
         mode: "htmlmixed",
         readOnly: "nocursor"
      });
      return codeMirrorEditor;

   }-*/;

   @Override
   public String getText()
   {
      return content;
   }

   @Override
   public void setText(String text)
   {
      textArea.setValue(text);
      codeMirror = initCodeMirror(textArea);
      content = text;
   }

   public native void refresh() /*-{
      var codeMirror = this.@org.zanata.webtrans.client.ui.CodeMirrorReadOnlyWidget::codeMirror;
      codeMirror.refresh();
   }-*/;

   interface CodeMirrorWidgetUiBinder extends UiBinder<Widget, CodeMirrorReadOnlyWidget>
   {
   }
}