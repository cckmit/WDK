import React, { Component } from 'react';
import { isLeaf } from '../utils/TreeUtils';
import IndeterminateCheckbox from './IndeterminateCheckbox';
import ReactElement = React.ReactElement;
import ComponentClass = React.ComponentClass;

const visibleElement = {display: ""};
const hiddenElement = {display: "none"};

type TreeRadioProps<T> = {
  name: string;
  checked: boolean;
  value: string;
  node: T;
  onChange: (node: T, checked: boolean) => void;
  className: string;
}

class TreeRadio<T> extends Component<TreeRadioProps<T>, void> {

  handleClick() {
    let { checked, onChange, node } = this.props;
    if (!checked) {
      onChange(node, false);
    }
  }

  render() {
    let { name, checked, value, className } = this.props;
    return (
      <input type="radio" className={className} name={name} value={value} checked={checked} onChange={this.handleClick.bind(this)} />
    );
  }
}


type NodeState = {
  isSelected: boolean;
  isIndeterminate: boolean;
  isVisible: boolean;
  isExpanded: boolean;
}

type Props<T> = {
  node: T;
  name: string;
  path: number[];
  listClassName: string;
  getNodeState: (node: T) => NodeState;
  isSelectable: boolean;
  isMultiPick: boolean;
  isActiveSearch: boolean;
  toggleExpansion: (node: T) => void;
  toggleSelection: (node: T, checked: boolean) => void;
  getNodeId: (node: T) => string;
  getNodeChildren: (node: T) => T[];
  nodeComponent: React.ComponentClass<{ node: T, path?: number[] }> | React.StatelessComponent<{ node: T, path?: number[] }>;
}

class CheckboxTreeNode<T> extends Component<Props<T>, void> {

  toggleExpansion = () => {
    this.props.toggleExpansion(this.props.node);
  }

  shouldComponentUpdate(nextProps: Props<T>) {
    return (nextProps.node !== this.props.node);
  }

  render(): ReactElement<Props<T>> {
    let {
      name,
      node,
      path,
      listClassName,
      getNodeState,
      isSelectable,
      isMultiPick,
      isActiveSearch,
      toggleSelection,
      toggleExpansion,
      getNodeId,
      getNodeChildren,
      nodeComponent
    } = this.props;


    // We have to apply the generic type `T` to these child components. This is
    // a known TypeScript issue and will likely be solved in the future.
    const IndeterminateCheckboxT = IndeterminateCheckbox as new () => IndeterminateCheckbox<T>;
    const TreeRadioT = TreeRadio as new () => TreeRadio<T>;

    let { isSelected, isIndeterminate, isVisible, isExpanded } = getNodeState(node);
    let isLeafNode = isLeaf(node, getNodeChildren);
    let nodeVisibilityCss = isVisible ? visibleElement : hiddenElement;
    let childrenVisibilityCss = isExpanded ? visibleElement : hiddenElement;
    let nodeType = isLeafNode ? "leaf"
                 : isExpanded ? "expanded"
                 : "collapsed";
    let NodeComponent = nodeComponent;
    let classNames = 'wdk-CheckboxTreeItem wdk-CheckboxTreeItem__' + nodeType +
      (isSelectable ? ' wdk-CheckboxTreeItem__selectable' : '');
    let inputName = isLeafNode ? name : '';

    return (
      <li className={classNames} style={nodeVisibilityCss}>
        <div className="wdk-CheckboxTreeNodeWrapper">
          {isLeafNode || isActiveSearch ? (
            <i className="wdk-CheckboxTreeToggle"/>
          ) : (
            <i
              className={'fa fa-caret-' + (isExpanded ? 'down ' : 'right ') +
                'wdk-CheckboxTreeToggle wdk-CheckboxTreeToggle__' + (isExpanded ? 'expanded' : 'collapsed') }
              onClick={this.toggleExpansion}
            />
          )}
          {!isSelectable || (!isMultiPick && !isLeafNode) ? (
            <div className="wdk-CheckboxTreeNodeContent" onClick={this.toggleExpansion}>
              <NodeComponent node={node} path={path} />
            </div>
          ) : (
            <label className="wdk-CheckboxTreeNodeContent">
              {isMultiPick ?
                <IndeterminateCheckboxT
                  className="wdk-CheckboxTreeCheckbox"
                  name={inputName}
                  checked={isSelected}
                  indeterminate={isIndeterminate}
                  node={node}
                  value={getNodeId(node)}
                  toggleCheckbox={toggleSelection} /> :
                <TreeRadioT
                  className="wdk-CheckboxTreeCheckbox"
                  name={inputName}
                  checked={isSelected}
                  value={getNodeId(node)}
                  node={node}
                  onChange={toggleSelection} />
              } <NodeComponent node={node} />
            </label>
          )}
        </div>
        {isLeafNode ? null :
          <ul className={listClassName} style={childrenVisibilityCss}>
            {getNodeChildren(node).map((child, index) =>
              <CheckboxTreeNode
                key={"node_" + getNodeId(child)}
                name={name}
                node={child}
                path={path.concat(index)}
                listClassName={listClassName}
                getNodeState={getNodeState}
                isSelectable={isSelectable}
                isMultiPick={isMultiPick}
                isActiveSearch={isActiveSearch}
                toggleSelection={toggleSelection}
                toggleExpansion={toggleExpansion}
                getNodeId={getNodeId}
                getNodeChildren={getNodeChildren}
                nodeComponent={nodeComponent} />
            )}
          </ul>
        }
      </li>
    );
  }
}

export default CheckboxTreeNode;