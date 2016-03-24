// Action types
export let actionTypes = {
  USER_LOADING: 'user/loading',
  USER_INITIALIZE_STORE: 'user/initialize',
  USER_PROFILE_UPDATE: 'user/profile-update',
  USER_PROPERTY_UPDATE: 'user/property-update',
  USER_PREFERENCE_UPDATE: 'user/preference-update',
  APP_ERROR: 'user/error'
};

export function loadCurrentUser() {
  return function run(dispatch, { wdkService }) {
    dispatch({ type: actionTypes.USER_LOADING });

    let userPromise = wdkService.getCurrentUser();
    let preferencePromise = wdkService.getCurrentUserPreferences();

    return Promise.all([ userPromise, preferencePromise ])
    .then(([ user, preferences ]) => {
      dispatch({
        type: actionTypes.USER_INITIALIZE_STORE,
        payload: { user, preferences }
      });
      return { user, preferences };
    }, error => {
      dispatch({
        type: actionTypes.APP_ERROR,
        payload: { error }
      });
      throw error;
    });
  };
}
