import React, { Component, FormEvent, MouseEvent, PropTypes } from 'react';
import {uniqueId, noop} from 'lodash';
import { wrappable } from '../utils/componentUtils';

type Item = {
  value: any
  display: string
}

type Props = {

  /** Value to use for "name" attribute of checkbox form input element **/
  name: string

  /** Array of items to display in the list **/
  items: Item[]

  /**
   * Default list of selected items. If provided, these items will be checked
   * for the initial render.
   */
  defaultSelectedItems?: Item[]

  /**
   * List of selected items.
   *
   * - If omitted, this component will track state locally.
   *
   * - If provided, items in this list will be checked, regardless of the value
   *   of `defaultSelectedItems`. Also, state will not be tracked locally.
   *   `onChange` should be used to detect changes and update extrernally
   *   tracked state.
   */
  selectedItems: Item[]

  /**
   * Callback function that will be called when the set of selected items
   * changes. The function will be called with two arguments:
   *
   *   - The event that triggered the change. This gives consumers a change to
   *     revert the state of the checked items.
   *
   *   - An array of checked items.
   */
  onChange?: (event: FormEvent<HTMLInputElement>, item: Item) => void

  /**
   * Called when the "select all" link is clicked.
   * If state is managed locally, all items will be checked.
   */
  onSelectAll?: (event: MouseEvent<HTMLAnchorElement>) => void

  /**
   * Called when the "clear all" link is clicked.
   * If state is managed locall, all items will be unchecked.
   */
  onClearAll?: (event: MouseEvent<HTMLAnchorElement>) => void
}

type State = {
  selectedItems: Item[]
}

/**
 * Render a list of checkboxes. Checkbox state is managed locally, unless
 * `seletedItems` is provided as a prop. (See this component's propTypes
 * documentation.)
 */
class NativeCheckboxList extends Component<Props, State> {

  id = uniqueId('NativeCheckboxList.')

  controlled = this.props.selectedItems != null

  static defaultProps = {
    defaultSelectedItems: [] as Item[],
    onChange: (event: FormEvent<HTMLInputElement>, item: Item) => {},
    onSelectAll: (event: MouseEvent<HTMLAnchorElement>) => {},
    onClearAll: (event: MouseEvent<HTMLAnchorElement>) => {}
  } as Props

  constructor(props: Props) {
    super(props);

    if (!this.controlled) {
      this.state = {
        selectedItems: this.props.defaultSelectedItems!
      };
    }
  }

  toggle(event: React.FormEvent<HTMLInputElement>, item: Item) {
    this.props.onChange!(event, item);

    if (!this.controlled && !event.defaultPrevented) {
      this.setState({
        selectedItems: event.currentTarget.checked
          ? this.state.selectedItems.concat(item.value)
          : this.state.selectedItems.filter(i => i.value !== item.value)
      });
    }
  }

  selectAll(event: React.MouseEvent<HTMLAnchorElement>) {
    this.props.onSelectAll!(event);

    if (!this.controlled && !event.defaultPrevented) {
      this.setState({
        selectedItems: this.props.items.map(item => item.value)
      });
    }

    // prevent update to URL
    event.preventDefault();
  }

  clearAll(event: React.MouseEvent<HTMLAnchorElement>) {
    this.props.onClearAll!(event);

    if (!this.controlled && !event.defaultPrevented) {
      this.setState({
        selectedItems: []
      });
    }

    // prevent update to URL
    event.preventDefault();
  }

  render() {
    let { selectedItems } = this.controlled ? this.props : this.state;
    return (
      <div className="wdk-CheckboxList">
        <div>
          {this.props.items.map(item => {
            let id = `${this.id}.${item.value}`;
            return (
              <div key={item.value} className="wdk-CheckboxListItem">
                <label>
                  <input
                    id={id}
                    type="checkbox"
                    name={this.props.name}
                    value={item.value}
                    checked={selectedItems.includes(item.value)}
                    onChange={e => this.toggle(e, item)}
                  />
                  {' ' + item.display}
                </label>
              </div>
            );
          })}
        </div>
        <div>
          <a href="#" onClick={e => this.selectAll(e)}>select all</a>
          {' | '}
          <a href="#" onClick={e => this.clearAll(e)}>clear all</a>
        </div>
      </div>
    );
  }
}

export default wrappable(NativeCheckboxList)