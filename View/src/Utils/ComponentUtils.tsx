import React from 'react';
import {AttributeValue} from 'Utils/WdkModel';

/**
 * React Component utils
 */

interface AnyObject {
  [key: string]: any;
}


/**
 * Stateless function component decorator that prevents rerendering
 * when props are equal use shallow comparison.
 */
export function pure<P>(Component: React.StatelessComponent<P>) {
  return class PureWrapper extends React.PureComponent<P, void> {
    static get displayName() {
      return `PureWrapper(${Component.displayName || Component.name})`;
    }
    render() {
      return (
        <Component {...this.props}/>
      );
    }
  }
}

interface ComponentWrapper<P> extends React.ComponentClass<P> {
  wrapComponent(factory: (Component: React.ComponentType<P>) => React.ComponentType<P>): void;
}

/**
 * A Component decorator that wraps the Component passed to it and returns a new
 * Component with a static method `wrapComponent`. Use this method to replace
 * the wrapped Component. See the docs for `wrapComponent` for more details.
 *
 *
 * Rationale
 * =========
 *
 * Components in WDK should be customizable by the application. The application
 * should be able to replace the Component, surround the Component, or modify
 * the props passed to the Component. By creating a wrapper Component whose
 * internal Component can be replaced in-place, WDK code does not need to do
 * any kind of lookup, and implementors are given a nice, convenient syntax for
 * what will most likely be a common task: modifying a WDK Component.
 *
 * There are other approaches that would require a uniform JavaScript build
 * environment, which would allow the application to perform overriding at
 * build time (similar to how we do this with JSP tags). We aren't quite at a
 * point where we can do this, and it might not be desirable.
 *
 *
 * Usage
 * =====
 *
 *     // As a function
 *     let Answer = wrappable(Answer);
 *
 *
 *     // As an ES2016 class decorator
 *     @wrappable
 *     class Answer extends React.Component {
 *       // ...
 *     }
 *
 *     // Using the static wrap method. Note that the function passed to `wrap`
 *     // will receive the currently wrapped Component as an argument. This
 *     // makes it possible to replace or decorate the Component, or to modify
 *     // the props passed to the component.
 *     Answer.wrap(function(Answer) {
 *       return React.createClass({
 *         render() {
 *           return (
 *             <div>
 *               <h1>My Custom title</h1>
 *               <Answer {...this.props}/>
 *             </div>
 *           );
 *         }
 *       });
 *     });
 *
 */
export function wrappable<P>(Component: React.ComponentType<P>): ComponentWrapper<P> {
  return class Wrapper extends React.PureComponent<P> {

    // Forward calls for displayName and propTypes to the wrapped Component.
    // This is useful for debugging messages generated by React.

    static get displayName() {
      return `WdkWrapper(${Component.displayName || Component.name})`;
    }

    static get propTypes() {
      return Component.propTypes;
    }

    /**
     * Used to modify the Component being wrapped. Use it by passing a function
     * that returns a new Component.
     *
     * The function will receive the current Component as an argument. This
     * makes it possible to render the current Component in the new component,
     * similar to Aspect Oriented Programming techniques.
     *
     * The new Component will replace the existing Component.
     *
     * TODO Verify that factory returns a React class
     *
     * @param {function} factory A factory function returning a new React
     *  Component. The function will receive the current Component as its sole
     *  param.
     */
    static wrapComponent(factory: (Component: React.ComponentType<P>) => React.ComponentType<P>) {
      Component = factory(Component);
    }

    render() {
      return <Component {...this.props}/>;
    }

  };
}

interface LoadCallback {
  (render: (props?: {}) => void): void
}

type LazyEnhance = <P>(Component: React.ComponentClass<P> | React.StatelessComponent<P>) => React.ComponentClass<P>


/**
 * A higher order component that allows a component to be rendered lazily.
 *
 * @example
 * lazy(function(render) {
 *   loadData('/some/data').then(function(data) {
 *     render({ data });
 *   })
 * })(ComponentThatNeedsData);
 */
export function lazy(load: LoadCallback): LazyEnhance {
  return function<P>(Component: React.ComponentClass<P> | React.StatelessComponent<P>) {
    return class Lazy extends React.Component<P, { loading: boolean, props: P }> {
      displayName = `Lazy(${Component.displayName || Component.name})`;
      constructor(props: P) {
        super(props);
        this.state = { loading: true, props }
      }
      componentDidMount() {
        load((props: P) => {
          this.setState({ loading: false, props: props || {} });
        })
      }
      render() {
        return this.state.loading ? null :
          <Component {...this.props} {...this.state.props}/>
      }
    }
  }
}


interface InstrumentOptions {
  compareProps?: boolean;
}
/**
 * Takes a component and returns an intrumented wrapper component
 * that will log details about props, etc.
 *
 * This should never be used in production code!
 */
export function instrument<P>(Component: React.ComponentClass<P>, options: InstrumentOptions): React.ComponentClass<P>;
export function instrument<P>(Component: React.StatelessComponent<P>, options: InstrumentOptions): React.ComponentClass<P>;
export function instrument<P>(Component: any, options: InstrumentOptions = {}): React.ComponentClass<P> {
  let {
    compareProps = true
  } = options;
  let componentName = Component.displayName || Component.name || Component;

  return class InstrumentWrapper extends React.Component<P> {
    shouldComponentUpdate(nextProps: P) {
      if (compareProps) {
        logShallowComparison(
          this.props,
          nextProps,
          'Comparing props for ' + componentName
        );
      }
      return true;
    }
    render() {
      return <Component {...this.props}/>;
    }
  }
}

/** Helper to log the results of a shallow comparison */
function logShallowComparison<P extends AnyObject>(obj1: P, obj2: P, label: string = 'Shallow comparison') {
  console.group(label);
  console.log('Comparing %o and %o', obj1, obj2);
  let allKeys = new Set(Object.keys(obj1).concat(Object.keys(obj2)));
  for (let key of allKeys) {
    let equal = obj1[key] === obj2[key];
    if (!equal) {
      console.log('`%s` not equal', key);
    }
  }
  console.groupEnd();
}


/** Create a React Element using preformatted HTML */
export function safeHtml<P>(str: string, props?: P, Component?: React.ComponentClass<P>): JSX.Element;
export function safeHtml<P>(str: string, props?: P, Component?: React.StatelessComponent<P>): JSX.Element;
export function safeHtml<P>(str: string, props?: P, Component?: string): JSX.Element;
export function safeHtml<P>(str = '', props?: P, Component: any = 'span'): JSX.Element {
  // Use innerHTML to auto close tags
  let container = document.createElement('div');
  container.innerHTML = str;
  return <Component {...props} dangerouslySetInnerHTML={{ __html: container.innerHTML }}/>;
}

/**
 * Makes a copy of the passed original object, subtracting the properties with
 * names in the propsToFilter arg, which should be Array[String].
 */
export function filterOutProps<P extends AnyObject>(orig: P, propsToFilter: string[]) {
  return Object.keys(orig).reduce((obj, key) =>
    (propsToFilter.indexOf(key) !== -1 ? obj :
      Object.assign(obj, { [key]: orig[key] })), {});
}

/**
 * Generates HTML markup for an attribute using duck-typing
 */
export function formatAttributeValue(value?: AttributeValue): string {
  if (typeof value === 'object' && value != null) {
    return `<a href="${value.url}">${value.displayText || value.url}</a>`;
  }
  return value as string;
}

/**
 * Creates a React-renderable element using the provided `Component`, or 'span'
 * by default.
 * TODO Look up or inject custom formatters
 */
export function renderAttributeValue<P>(value: AttributeValue, props?: P, Component = 'span') {
  return safeHtml(
    formatAttributeValue(value),
    props,
    Component
  );
}

/**
 * Makes a copy of current, adds value if not present, removes if present, and
 * returns the copy.
 * @param {Array<T>} array array to modify
 * @param {<T>} value to check against
 * @return {Array<T>} modified copy of original array
 */
export function addOrRemove<T>(array: T[], value: T) : T[] {
  return (array.indexOf(value) == -1 ?
    // not currently present; add
    array.concat(value) :
    // already there; remove
    array.filter(elem => elem != value));
}

/**
 * Looks for the property with the passed name in the given object.  If the
 * object or the property is null or undefined, returns default value.
 * Otherwise returns the value found.
 */
export function getValueOrDefault<T>(object: AnyObject, propertyName: string, defaultValue: T): T {
  return (object == null || object == undefined ||
      object[propertyName] == null || object[propertyName] == undefined ?
      defaultValue : object[propertyName]);
}

/**
 * Returns a change handler that will 'bubble' a state change to the
 * onParentChange function passed in.  The value passed to the parent handler is
 * a copy of previousState with a new value applied to the name this function
 * was called with.
 */
export function getChangeHandler<S extends {}, T>(inputName: string, onParentChange: (s: S) => S, previousState: S) {
  return (newValue: T) => {
    onParentChange(Object.assign({}, previousState, { [inputName]: newValue }));
  };
}

/**
 * For each property in propertyNameList, examines each property in both
 * oldProps and newProps to check for referential equality.  If any prop differs
 * returns true, else returns false.
 *
 * @param {Object} oldProps props object
 * @param {Object} newProps another props object
 * @param {Array<String>} propertyNameList list of properties to examine
 * @return {boolean} false if all properties are referentially identical, else true
 */
export function propsDiffer<P extends AnyObject>(oldProps: P, newProps: P, propertyNameList: string[]) {
  for (let i = 0; i < propertyNameList.length; i++) {
    if (oldProps[propertyNameList[i]] !== newProps[propertyNameList[i]]) {
      return true;
    }
  }
  return false;
}

interface HandlerSetObject {
  [key: string]: (...args: any[]) => any;
}

/**
 * Bind a collection of action functions to a dispatchAction function.
 *
 * @param {Function} dispatchAction
 * @param {Object<Function>} actions
 */
export function wrapActions(dispatchAction: Function, ...actionObjects: HandlerSetObject[]): HandlerSetObject {
  let wrappedActions: HandlerSetObject = {};
  for (let actionObject of actionObjects) {
    for (let key in actionObject) {
      wrappedActions[key] = function wrappedAction(...args: any[]): Function {
        return dispatchAction(actionObject[key](...args));
      }
    }
  }
  return wrappedActions;
}

/**
 * Takes an object containing named functions and an object (usually 'this')
 * and returns a new object containing copies of the original functions that
 * are bound to objectToBind.
 *
 * @param {Object} handlerSetObject set of named functions
 * @param {Object} objectToBind object to which copies of named functions will be bound
 * @return {Object} copy of handlerSetObject with newly bound copies of original functions
 */
export function bindEventHandlers(handlerSetObject: HandlerSetObject, objectToBind: any): HandlerSetObject {
  let newHandlers: HandlerSetObject = {};
  for (let key in handlerSetObject) {
    newHandlers[key] = handlerSetObject[key].bind(objectToBind);
  }
  return newHandlers;
}

/**
 * Create a helper for generating classNames that follow a BEM-inspired naming
 * convention.
 *
 * @example
 * ```
 * let makeClassName = makeClassNameHelper('wdk-Page');
 * makeClassName(); //=> 'wdk-Page'
 * makeClassName('Title'); //=> 'wdk-PageTitle'
 * makeClassName('Title', 'muted'); //=> 'wdk-PageTitle wdk-PageTitle__muted'
 * makeClassName('Title', 'muted', 'blue'); //=> 'wdk-PageTitle wdk-PageTitle__muted wdk-PageTitle__blue'
 * ```
 */
export function makeClassNameHelper(baseClassName: string) {
  return function makeClassName(suffix = '', ...modifiers: any[]) {
    let className = baseClassName + suffix;
    let modifiedClassNames = modifiers
      .filter(modifier => typeof modifier === 'string' && modifier !== '')
      .map(modifier => ' ' + className + '__' + modifier)
      .join('');

    return className + modifiedClassNames;
  }
}