import React, { FormEvent } from 'react';
import { mount } from 'enzyme';
import { act } from 'react-dom/test-utils';
import CounterfactualAnalysis from '../CounterfactualAnalysis';
import {
  CFAnalysisResultsSets,
  CFExecutionStatus,
  CFGoalRole,
  ItemObject,
  Outcome
} from '../../../../types';
import useCounterfactualExecution from '../useCounterfactualExecution';

jest.mock('../useCounterfactualExecution');
jest.mock(
  '../../../Molecules/CounterfactualProgressBar/CounterfactualProgressBar',
  () => () => <div className="progress-bar" />
);

describe('CounterfactualAnalysis', () => {
  test('renders correctly', () => {
    (useCounterfactualExecution as jest.Mock).mockReturnValue({
      runCFAnalysis,
      cfResults: undefined
    });
    const wrapper = mount(
      <CounterfactualAnalysis
        inputs={inputs}
        outcomes={outcomes}
        executionId={executionId}
        containerHeight={900}
        containerWidth={900}
      />
    );

    expect(wrapper).toMatchSnapshot();
  });

  test('has the correct initial state', () => {
    (useCounterfactualExecution as jest.Mock).mockReturnValue({
      runCFAnalysis,
      cfResults: undefined
    });
    const wrapper = mount(
      <CounterfactualAnalysis
        inputs={inputs}
        outcomes={outcomes}
        executionId={executionId}
        containerHeight={900}
        containerWidth={900}
      />
    );

    expect(
      wrapper.find('Button#counterfactual-run').props()['isAriaDisabled']
    ).toBeTruthy();
    expect(wrapper.find('CounterfactualOutcomesSelected').text()).toMatch('');
    expect(wrapper.find('CounterfactualTable Thead Tr')).toHaveLength(1);
    expect(wrapper.find('CounterfactualTable Tbody Tr')).toHaveLength(2);
    expect(
      wrapper
        .find('CounterfactualTable Tbody Tr Td')
        .at(1)
        .text()
    ).toMatch('Credit Score');
    expect(
      wrapper
        .find('CounterfactualTable Tbody Tr Td')
        .at(3)
        .text()
    ).toMatch('738');
    expect(
      wrapper
        .find('CounterfactualTable Tbody Tr')
        .at(1)
        .find('Td')
        .at(1)
        .text()
    ).toMatch('Type');
    expect(
      wrapper
        .find('CounterfactualTable Tbody Tr')
        .at(1)
        .find('Td')
        .at(3)
        .text()
    ).toMatch('Lease');

    expect(
      wrapper
        .find('CounterfactualTable Tbody Tr Td')
        .at(4)
        .text()
    ).toMatch('No available results');
  });

  test('provides an hint on how to setup an analysis', () => {
    (useCounterfactualExecution as jest.Mock).mockReturnValue({
      runCFAnalysis,
      cfResults: undefined
    });
    const wrapper = mount(
      <CounterfactualAnalysis
        inputs={inputs}
        outcomes={outcomes}
        executionId={executionId}
        containerHeight={900}
        containerWidth={900}
      />
    );

    expect(wrapper.find('CounterfactualHint')).toHaveLength(1);
    expect(wrapper.find('CounterfactualHint Hint')).toHaveLength(1);

    wrapper
      .find('CounterfactualHint button#hint-kebab-toggle')
      .simulate('click');
    wrapper.find('CounterfactualHint #hint-close button').simulate('click');

    expect(wrapper.find('CounterfactualHint Hint')).toHaveLength(0);
  });

  test('handles input selection, constraints change and outcome selection', async () => {
    const results = {
      runCFAnalysis,
      cfResults: undefined
    };

    (useCounterfactualExecution as jest.Mock).mockReturnValue(results);

    const wrapper = mount(
      <CounterfactualAnalysis
        inputs={inputs}
        outcomes={outcomes}
        executionId={executionId}
        containerHeight={900}
        containerWidth={900}
      />
    );
    expect(wrapper.find('CounterfactualInputDomainEdit')).toHaveLength(0);
    expect(
      wrapper.find('Button#counterfactual-run').props()['isAriaDisabled']
    ).toBeTruthy();

    wrapper
      .find(
        'CounterfactualTable Tbody Tr:first-child Td:first-child SelectColumn'
      )
      .simulate('change');

    wrapper
      .find('CounterfactualTable Tbody Tr:first-child Td')
      .at(2)
      .find('Button')
      .simulate('click');

    expect(wrapper.find('CounterfactualInputDomainEdit')).toHaveLength(1);
    expect(wrapper.find('CounterfactualNumericalDomainEdit')).toHaveLength(1);

    const lowerBound = wrapper
      .find('CounterfactualNumericalDomainEdit SplitItem')
      .at(0)
      .find('input');
    lowerBound.getDOMNode<HTMLInputElement>().value = '1';
    lowerBound.simulate('change', '1');

    const upperBound = wrapper
      .find('CounterfactualNumericalDomainEdit SplitItem')
      .at(1)
      .find('input');
    upperBound.getDOMNode<HTMLInputElement>().value = '10';
    upperBound.simulate('change', '10');

    wrapper
      .find('CounterfactualInputDomainEdit ActionListItem:first-child Button')
      .simulate('click');

    expect(wrapper.find('CounterfactualInputDomainEdit')).toHaveLength(0);
    expect(
      wrapper
        .find('CounterfactualTable Tbody Tr:first-child Td')
        .at(2)
        .find('Button')
        .text()
    ).toMatch('1-10');

    wrapper
      .find('CounterfactualTable Tbody Tr')
      .at(1)
      .find('Td:first-child SelectColumn')
      .simulate('change');

    wrapper
      .find('CounterfactualTable Tbody Tr')
      .at(1)
      .find('Td')
      .at(2)
      .find('Button')
      .simulate('click');

    expect(wrapper.find('CounterfactualInputDomainEdit')).toHaveLength(1);
    expect(wrapper.find('CounterfactualCategoricalDomainEdit')).toHaveLength(1);

    const firstEnum = wrapper.find(
      'CounterfactualCategoricalDomainEdit input#enum-value-0'
    );
    firstEnum.getDOMNode<HTMLInputElement>().value = 'alpha';
    firstEnum.simulate('change', 'alpha');
    firstEnum.simulate('blur');

    wrapper
      .find('CounterfactualCategoricalDomainEdit button#enum-add')
      .simulate('click');

    const secondEnum = wrapper.find(
      'CounterfactualCategoricalDomainEdit input#enum-value-1'
    );
    secondEnum.getDOMNode<HTMLInputElement>().value = 'beta';
    secondEnum.simulate('change', 'beta');
    secondEnum.simulate('blur');

    wrapper
      .find('CounterfactualInputDomainEdit ActionListItem:first-child Button')
      .simulate('click');

    expect(wrapper.find('CounterfactualInputDomainEdit')).toHaveLength(0);
    expect(wrapper.find('CounterfactualCategoricalDomainEdit')).toHaveLength(0);

    expect(
      wrapper
        .find('CounterfactualTable Tbody Tr')
        .at(1)
        .find('Td')
        .at(2)
        .find('Button')
        .text()
    ).toMatch('alpha, beta');

    expect(wrapper.find('CounterfactualOutcomeSelection')).toHaveLength(0);
    expect(
      wrapper.find('Button#counterfactual-run').props()['isAriaDisabled']
    ).toBeTruthy();

    wrapper.find('Button#counterfactual-setup-outcomes').simulate('click');

    expect(wrapper.find('CounterfactualOutcomeSelection')).toHaveLength(1);

    expect(
      wrapper
        .find('CounterfactualOutcomeSelection Button#confirm-outcome-selection')
        .props()['isAriaDisabled']
    ).toBeTruthy();

    expect(
      wrapper.find('CounterfactualOutcomeSelection CounterfactualOutcomeEdit')
    ).toHaveLength(3);

    expect(
      wrapper
        .find('CounterfactualOutcomeSelection CounterfactualOutcomeEdit')
        .at(2)
        .find('FormGroup')
        .props()['label']
    ).toMatch('Asset Score');

    await act(async () => {
      wrapper
        .find('CounterfactualOutcomeSelection CounterfactualOutcomeEdit')
        .at(2)
        .find('Checkbox.counterfactual-outcome__floating')
        .props()
        ['onChange']({ currentTarget: { checked: true } } as FormEvent<
          HTMLInputElement
        >);
    });
    wrapper.update();

    expect(
      wrapper
        .find('CounterfactualOutcomeSelection Button#confirm-outcome-selection')
        .props()['isAriaDisabled']
    ).toBeTruthy();

    expect(
      wrapper
        .find('CounterfactualOutcomeSelection CounterfactualOutcomeEdit')
        .at(0)
        .find('FormGroup')
        .props()['label']
    ).toMatch('Risk Score');

    wrapper
      .find('CounterfactualOutcomeSelection CounterfactualOutcomeEdit')
      .at(0)
      .find('Touchspin Button')
      .at(1)
      .simulate('click');

    expect(
      wrapper
        .find('CounterfactualOutcomeSelection Button#confirm-outcome-selection')
        .props()['isAriaDisabled']
    ).toBeFalsy();

    expect(
      wrapper
        .find('CounterfactualOutcomeSelection CounterfactualOutcomeEdit')
        .at(1)
        .find('FormGroup')
        .props()['label']
    ).toMatch('canRequestLoan');

    wrapper
      .find('CounterfactualOutcomeSelection CounterfactualOutcomeEdit')
      .at(1)
      .find('Switch input')
      .simulate('change', { target: { checked: true } });

    wrapper
      .find('CounterfactualOutcomeSelection Button#confirm-outcome-selection')
      .simulate('click');

    expect(wrapper.find('CounterfactualOutcomesSelected').text()).toMatch(
      'Selected Outcomes: Risk Score: 2, canRequestLoan: true, Asset Score: Any'
    );
    expect(
      wrapper.find('Button#counterfactual-run').props()['isAriaDisabled']
    ).toBeFalsy();

    wrapper.find('Button#counterfactual-run').simulate('click');

    expect(runCFAnalysis).toHaveBeenCalledWith({
      goals: [
        {
          id: '_c6e56793-68d0-4683-b34b-5e9d69e7d0d4',
          role: CFGoalRole.FIXED,
          kind: 'UNIT',
          name: 'Risk Score',
          originalValue: 1,
          typeRef: 'number',
          value: 2
        },
        {
          id: '_46B5CA54-27CA-4950-B601-63F58BC3BDFE',
          role: CFGoalRole.FIXED,
          kind: 'UNIT',
          name: 'canRequestLoan',
          originalValue: false,
          typeRef: 'boolean',
          value: true
        },
        {
          id: '_047FFF53-0583-4FAD-B08F-8E1C077021D6',
          role: CFGoalRole.FLOATING,
          kind: 'UNIT',
          name: 'Asset Score',
          originalValue: '33',
          typeRef: 'number',
          value: '33'
        }
      ],
      searchDomains: [
        {
          components: null,
          domain: {
            type: 'RANGE',
            lowerBound: 1,
            upperBound: 10
          },
          fixed: false,
          kind: 'UNIT',
          name: 'Credit Score',
          typeRef: 'number',
          value: 738
        },
        {
          components: null,
          domain: {
            categories: ['alpha', 'beta'],
            type: 'CATEGORICAL'
          },
          fixed: false,
          kind: 'UNIT',
          name: 'Type',
          typeRef: 'string',
          value: 'Lease'
        }
      ]
    });
  });

  test('renders counterfactual results', () => {
    (useCounterfactualExecution as jest.Mock).mockReturnValue({
      runCFAnalysis,
      cfResults: cfResultsFinal
    });
    const wrapper = mount(
      <CounterfactualAnalysis
        inputs={inputs}
        outcomes={outcomes}
        executionId={executionId}
        containerHeight={900}
        containerWidth={900}
      />
    );

    expect(
      wrapper
        .find('CounterfactualTable Tbody Tr:first-child Td')
        .at(5)
        .text()
    ).toMatch(`ID #${cfResultsFinal.solutions[0].solutionId}`);

    expect(
      wrapper
        .find('CounterfactualTable Tbody Tr')
        .at(1)
        .find('Td')
        .at(5)
        .text()
    ).toMatch(cfResultsFinal.solutions[0].inputs[0].value.toString());

    expect(
      wrapper.find('CounterfactualCompletedMessage').props()['status']
        .executionStatus
    ).toEqual(CFExecutionStatus.COMPLETED);

    expect(
      wrapper
        .find('CounterfactualExecutionInfo .cf-execution-info__results Badge')
        .text()
    ).toMatch('1');
  });

  test('handles an analysis with no results', () => {
    (useCounterfactualExecution as jest.Mock).mockReturnValue({
      runCFAnalysis,
      cfResults: cfNoResults
    });
    const wrapper = mount(
      <CounterfactualAnalysis
        inputs={inputs}
        outcomes={outcomes}
        executionId={executionId}
        containerHeight={900}
        containerWidth={900}
      />
    );

    expect(wrapper.find('CounterfactualTable Thead Tr Th')).toHaveLength(4);

    expect(
      wrapper.find('CounterfactualCompletedMessage').props()['status']
        .executionStatus
    ).toEqual(CFExecutionStatus.NO_RESULTS);
  });

  test('handles a failed analysis', () => {
    (useCounterfactualExecution as jest.Mock).mockReturnValue({
      runCFAnalysis,
      cfResults: cfResultsFailed
    });
    const wrapper = mount(
      <CounterfactualAnalysis
        inputs={inputs}
        outcomes={outcomes}
        executionId={executionId}
        containerHeight={900}
        containerWidth={900}
      />
    );

    expect(wrapper.find('CounterfactualTable Thead Tr Th')).toHaveLength(4);

    expect(
      wrapper.find('CounterfactualCompletedMessage').props()['status']
        .executionStatus
    ).toEqual(CFExecutionStatus.FAILED);
  });

  test('lets the user start another analysis', () => {
    (useCounterfactualExecution as jest.Mock).mockReturnValue({
      runCFAnalysis,
      cfResults: cfResultsFinal
    });
    const wrapper = mount(
      <CounterfactualAnalysis
        inputs={inputs}
        outcomes={outcomes}
        executionId={executionId}
        containerHeight={900}
        containerWidth={900}
      />
    );
    expect(wrapper.find('Button#counterfactual-run')).toHaveLength(0);

    expect(wrapper.find('Button#counterfactual-new')).toHaveLength(1);
    wrapper.find('Button#counterfactual-new').simulate('click');

    expect(wrapper.find('CounterfactualToolbar Modal')).toHaveLength(1);
    expect(
      wrapper.find('CounterfactualToolbar Modal').props()['title']
    ).toMatch('Results will be cleared');
    wrapper
      .find('CounterfactualToolbar Modal ModalBoxFooter Button')
      .at(0)
      .simulate('click');

    expect(wrapper.find('Button#counterfactual-run')).toHaveLength(1);
    expect(
      wrapper.find('Button#counterfactual-run').props()['isAriaDisabled']
    ).toBeTruthy();
    expect(
      wrapper
        .find(
          'CounterfactualTable Tbody Tr:first-child Td:first-child SelectColumn'
        )
        .props()['checked']
    ).toBeFalsy();
    expect(
      wrapper
        .find('CounterfactualTable Tbody Tr:first-child Td')
        .at(2)
        .find('Button')
        .props()['isDisabled']
    ).toBeTruthy();
    expect(
      wrapper
        .find('CounterfactualTable Tbody Tr:first-child Td')
        .at(2)
        .find('Button')
        .text()
    ).toMatch('Constraint');
    expect(wrapper.find('CounterfactualOutcomesSelected').text()).toMatch('');
  });

  test('lets the user to reset the input/outcome selection', () => {
    const results = {
      runCFAnalysis,
      cfResults: undefined
    };

    (useCounterfactualExecution as jest.Mock).mockReturnValue(results);

    const wrapper = mount(
      <CounterfactualAnalysis
        inputs={inputs}
        outcomes={outcomes}
        executionId={executionId}
        containerHeight={900}
        containerWidth={900}
      />
    );

    wrapper
      .find('CounterfactualTable Tbody Tr')
      .at(1)
      .find('Td:first-child SelectColumn')
      .simulate('change');

    wrapper
      .find('CounterfactualTable Tbody Tr')
      .at(1)
      .find('Td')
      .at(2)
      .find('Button')
      .simulate('click');

    expect(wrapper.find('CounterfactualInputDomainEdit')).toHaveLength(1);
    expect(wrapper.find('CounterfactualCategoricalDomainEdit')).toHaveLength(1);

    const firstEnum = wrapper.find(
      'CounterfactualCategoricalDomainEdit input#enum-value-0'
    );
    firstEnum.getDOMNode<HTMLInputElement>().value = 'alpha';
    firstEnum.simulate('change', 'alpha');
    firstEnum.simulate('blur');

    wrapper
      .find('CounterfactualInputDomainEdit ActionListItem:first-child Button')
      .simulate('click');

    expect(
      wrapper
        .find('CounterfactualTable Tbody Tr')
        .at(1)
        .find('Td')
        .at(2)
        .find('Button')
        .text()
    ).toMatch('alpha');

    wrapper.find('Button#counterfactual-reset').simulate('click');

    const constraintButton = wrapper
      .find('CounterfactualTable Tbody Tr')
      .at(1)
      .find('Td')
      .at(2)
      .find('Button');

    expect(constraintButton.text()).toMatch('Constraint');
    expect(constraintButton.props()['isDisabled']).toBeTruthy();
  });
});

const inputs: ItemObject[] = [
  {
    components: null,
    name: 'Credit Score',
    kind: 'UNIT',
    typeRef: 'number',
    value: 738
  },
  {
    components: null,
    name: 'Type',
    kind: 'UNIT',
    typeRef: 'string',
    value: 'Lease'
  }
];

const outcomes: Outcome[] = [
  {
    evaluationStatus: 'SUCCEEDED',
    hasErrors: false,
    messages: [],
    outcomeId: '_c6e56793-68d0-4683-b34b-5e9d69e7d0d4',
    outcomeName: 'Risk Score',
    outcomeResult: {
      name: 'Risk Score',
      kind: 'UNIT',
      typeRef: 'number',
      value: 1,
      components: null
    }
  },
  {
    evaluationStatus: 'SUCCEEDED',
    hasErrors: false,
    messages: [],
    outcomeId: '_46B5CA54-27CA-4950-B601-63F58BC3BDFE',
    outcomeName: 'canRequestLoan',
    outcomeResult: {
      name: 'canRequestLoan',
      kind: 'UNIT',
      typeRef: 'boolean',
      value: false,
      components: null
    }
  },
  {
    evaluationStatus: 'SUCCEEDED',
    hasErrors: false,
    messages: [],
    outcomeId: '_047FFF53-0583-4FAD-B08F-8E1C077021D6',
    outcomeName: 'Asset Score',
    outcomeResult: {
      name: 'Asset Score',
      kind: 'UNIT',
      typeRef: 'number',
      value: '33',
      components: null
    }
  }
];
const runCFAnalysis = jest.fn();

const cfResultsFinal: CFAnalysisResultsSets = {
  executionId: '123456',
  counterfactualId: '789456',
  goals: [],
  searchDomains: [],
  solutions: [
    {
      counterfactualId: 'counterfactualId',
      executionId: 'b2b0ed8d-c1e2-46b5-3ac54ff4beae-1000',
      sequenceId: 343434,
      inputs: [
        {
          name: 'Credit Score',
          typeRef: 'number',
          kind: 'UNIT',
          value: 5,
          components: null
        },
        {
          name: 'Lease',
          typeRef: 'string',
          kind: 'UNIT',
          value: 'alpha',
          components: null
        }
      ],
      isValid: true,
      outputs: [],
      solutionId: '1031',
      stage: 'FINAL',
      status: 'SUCCEEDED',
      statusDetails: '',
      type: 'counterfactual',
      valid: true
    }
  ]
};

const cfNoResults: CFAnalysisResultsSets = {
  ...cfResultsFinal,
  solutions: [
    {
      ...cfResultsFinal.solutions[0],
      isValid: false
    }
  ]
};

const cfResultsFailed: CFAnalysisResultsSets = {
  ...cfResultsFinal,
  solutions: [
    {
      ...cfResultsFinal.solutions[0],
      status: 'FAILED'
    }
  ]
};

const executionId = 'b2b0ed8d-c1e2-46b5-3ac54ff4beae-1000';
