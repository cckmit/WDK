package org.gusdb.wdk.model.record.attribute;

import static org.gusdb.fgputil.FormatUtil.NL;

import java.util.ArrayList;
import java.util.List;

import org.gusdb.fgputil.functional.TreeNode;
import org.gusdb.wdk.model.FieldTree;
import org.gusdb.wdk.model.SelectableItem;
import org.gusdb.wdk.model.WdkModelBase;
import org.gusdb.wdk.model.WdkModelText;
import org.gusdb.wdk.model.record.FieldScope;
import org.gusdb.wdk.model.record.TableField;

/**
 * A tree structure used to organize the {@link AttributeField}s and {@link TableField}s in trees.
 * 
 * @author Ryan
 *
 */
public class AttributeCategory extends WdkModelBase {

	private String name;
	private String displayName;
	private String description;
	private List<AttributeCategory> subcategories = new ArrayList<AttributeCategory>();
	private List<AttributeField> fields = new ArrayList<AttributeField>();
	
	public void setName(String name) {
		this.name = name;
	}
	public String getName() {
		return name;
	}
	
	public void setDisplayName(String displayName) {
		this.displayName = displayName;
	}
	public String getDisplayName() {
		return displayName;
	}
	
	public String getDescription() {
    return description;
  }
  public void setDescription(WdkModelText description) {
    this.description = description.getText();
  }

  public void addAttributeCategory(AttributeCategory category) {
		subcategories.add(category);
	}
	public List<AttributeCategory> getSubCategories() {
		return subcategories;
	}

	public void addField(AttributeField field) {
		fields.add(field);
	}
	public void setFields(List<AttributeField> newFields) {
		fields = newFields;
	}
	public List<AttributeField> getFields() {
		return fields;
	}
	
	/**
	 * Creates copy of this object and returns it.  Deeply copies
	 * nested AttributeCategories, but only copies references to
	 * AttributeFields (List of fields is a copy as though).  Trims
	 * out subcategories that do not contain any fields.
	 * 
	 * @return copy of this AttribueCategory
	 */
	public AttributeCategory getTrimmedCopy(FieldScope scope) {
		AttributeCategory copy = new AttributeCategory();
		copy.name = name;
		copy.displayName = displayName;
		copy.description = description;
		for (AttributeCategory cat : subcategories) {
			AttributeCategory catCopy = cat.getTrimmedCopy(scope);
			if (!(catCopy.subcategories.isEmpty() && catCopy.fields.isEmpty())) {
				copy.subcategories.add(catCopy);
			}
		}
		for (AttributeField field : fields) {
			if (scope.isFieldInScope(field)) {
				copy.fields.add(field);
			}
		}
		return copy;
	}
	
	public void appendToStringBuffer(String indentation, StringBuilder builder) {
		builder.append(indentation).append(name).append(" (").append(fields.size()).append(")").append(NL);
		for (AttributeCategory cat : subcategories) {
			cat.appendToStringBuffer(indentation + "  ", builder);
		}
	}

	public FieldTree toFieldTree() {
	    return toFieldTree(getName(), getDisplayName(), subcategories, fields);
	}
	
	public static FieldTree toFieldTree(String rootName, String rootDisplayName,
	    List<AttributeCategory> rootSubcategories, List<AttributeField> rootSubfields) {
		FieldTree tree = new FieldTree(new SelectableItem(rootName, rootDisplayName));
		TreeNode<SelectableItem> root = tree.getRoot();
		for (AttributeCategory cat : rootSubcategories) {
			root.addChildNode(cat.toFieldTree().getRoot());
		}
		for (AttributeField attrib : rootSubfields) {
			root.addChild(new SelectableItem(attrib.getName(), attrib.getDisplayName(), attrib.getHelp()));
		}
		return tree;
	}
}
