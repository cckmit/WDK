import {pruneDescendantNodes, Node} from './TreeUtils';

export interface OntologyNode extends Node {
  properties: {[key: string]: Array<string>}
}

export interface Ontology {
  name: string;
  tree: OntologyNode;
}

/**
 * Get a sub-tree from an Ontology. The `leafPredicate` function
 * is used to find the leaves of the tree to return.
 *
 * @param {Ontology} ontology
 * @param {Function} leafPredicate
 */
export function getTree(ontology: Ontology, leafPredicate: (node: OntologyNode) => boolean) {
  return pruneDescendantNodes(node => nodeHasChildren(<OntologyNode>node) || leafPredicate(<OntologyNode>node), ontology.tree);
}

/**
 * Callback to provide the node children
 * @param node - given node
 * @returns {Array}  child nodes
 */
export let getNodeChildren = (node: OntologyNode) =>
  node.children;

export let nodeHasChildren = (node: OntologyNode) =>
  getNodeChildren(node).length > 0;

let includes = <T>(array: Array<T>, value: T) =>
  Boolean(array != null && array.indexOf(value) > -1);

export let nodeHasProperty = (name: string, value: string, node: OntologyNode) =>
  includes(node.properties[name], value);

export let getPropertyValues = (name: string, node: OntologyNode) =>
  (node.properties && node.properties[name]) || [];

export let getPropertyValue = (name: string, node: OntologyNode) =>
  node.properties && node.properties[name] && node.properties[name][0];
